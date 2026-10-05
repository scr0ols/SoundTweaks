package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.SoundTweaks;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Native open/save file dialogs for {@code .json} files, backed by SDL3.
 *
 * <p>SDL shows the dialog asynchronously, so each method returns immediately. The outcome is delivered on the
 * game thread: {@code onSelected} when the user picks a file, {@code onUnavailable} when SDL cannot show the
 * dialog (for example no desktop portal on Linux). Cancelling the dialog calls neither.
 *
 * <p>Must be called from the game thread, as SDL requires dialogs to be started from the main thread.
 */
public final class FileDialogs {

    private FileDialogs() {}

    /** Shows an "open file" dialog filtered to {@code .json}. */
    public static void openJson(Consumer<Path> onSelected, Runnable onUnavailable) {
        show(false, null, onSelected, onUnavailable);
    }

    /** Shows a "save file" dialog filtered to {@code .json}, pre-filled with {@code defaultName}. */
    public static void saveJson(String defaultName, Consumer<Path> onSelected, Runnable onUnavailable) {
        show(true, defaultName, path -> onSelected.accept(withJsonExtension(path)), onUnavailable);
    }

    /** SDL does not enforce the filter on the name the user types, so add the extension when it is missing. */
    private static Path withJsonExtension(Path path) {
        String name = path.getFileName().toString();
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".json") ? path : path.resolveSibling(name + ".json");
    }

    private static void show(boolean save, String defaultName, Consumer<Path> onSelected, Runnable onUnavailable) {
        Minecraft mc = Minecraft.getInstance();

        // SDL may read these until the callback runs, so they stay allocated until then.
        ByteBuffer filterName = MemoryUtil.memUTF8("JSON files");
        ByteBuffer filterPattern = MemoryUtil.memUTF8("json");
        SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
        filters.name(filterName).pattern(filterPattern);
        ByteBuffer location = defaultName == null ? null : MemoryUtil.memUTF8(defaultName);

        // The callback object must stay referenced until SDL calls it, and is freed afterwards.
        SDL_DialogFileCallback[] self = new SDL_DialogFileCallback[1];
        self[0] = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
            String selected = null;
            boolean failed = fileList == 0L;
            if (!failed) {
                long first = MemoryUtil.memGetAddress(fileList);
                if (first != 0L) selected = MemoryUtil.memUTF8(first);
            }
            String result = selected;
            boolean unavailable = failed;
            // SDL calls back from its own thread on some platforms; hand everything to the game thread,
            // including freeing the callback, which cannot be freed while it is still running.
            mc.execute(() -> {
                self[0].free();
                filters.free();
                MemoryUtil.memFree(filterName);
                MemoryUtil.memFree(filterPattern);
                if (location != null) MemoryUtil.memFree(location);
                deliver(result, unavailable, onSelected, onUnavailable);
            });
        });

        long window = mc.getWindow().handle();
        try {
            if (save) SDLDialog.SDL_ShowSaveFileDialog(self[0], 0L, window, filters, location);
            else      SDLDialog.SDL_ShowOpenFileDialog(self[0], 0L, window, filters, (ByteBuffer) null, false);
        } catch (Throwable t) {
            SoundTweaks.LOGGER.error("SoundTweaks: could not open the file dialog", t);
            self[0].free();
            filters.free();
            MemoryUtil.memFree(filterName);
            MemoryUtil.memFree(filterPattern);
            if (location != null) MemoryUtil.memFree(location);
            onUnavailable.run();
        }
    }

    private static void deliver(String selected, boolean unavailable,
                                Consumer<Path> onSelected, Runnable onUnavailable) {
        if (unavailable) {
            SoundTweaks.LOGGER.warn("SoundTweaks: the system file dialog is unavailable");
            onUnavailable.run();
            return;
        }
        if (selected == null) return; // cancelled
        try {
            onSelected.accept(Path.of(selected));
        } catch (InvalidPathException e) {
            SoundTweaks.LOGGER.error("SoundTweaks: the file dialog returned an invalid path: {}", selected, e);
            onUnavailable.run();
        }
    }
}
