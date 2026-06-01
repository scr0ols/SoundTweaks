package com.scr0ols.soundtweaks.client.gui;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Componente de dropdown reutilizável para os filtros de categoria e objecto.
 *
 * NÃO extende AbstractWidget — é gerido manualmente pelo SoundTweaksScreen
 * para garantir que a popup é sempre desenhada por cima de tudo o resto.
 *
 * Uso:
 *   1. Instanciar com posição, dimensões, label de placeholder e callback
 *   2. Chamar render() no extractRenderState() do Screen (APÓS todos os outros widgets)
 *   3. Chamar mouseClicked() e mouseScrolled() ANTES de os passar à UI normal
 */
public class FilterDropdown {

    // --- Dimensões e posição ---
    private int x, y;
    private final int width;
    private static final int BUTTON_HEIGHT  = 20;
    private static final int ITEM_HEIGHT    = 14; // altura de cada linha na popup
    private static final int MAX_VISIBLE    = 8;  // máximo de linhas visíveis sem scroll
    private static final int POPUP_PADDING  = 2;  // padding interior da popup

    // --- Estado ---
    private List<String> options     = new ArrayList<>(); // valores internos (ex: "piston")
    private List<String> labels      = new ArrayList<>(); // labels para display (ex: "Piston")
    private String selectedValue     = null;  // null = I18n.get("soundtweaks.gui.all") / nada seleccionado
    private boolean isOpen           = false;
    private int hoveredIndex         = -1;    // índice com hover na popup (-1 = nenhum)
    private int scrollOffset         = 0;     // quantas linhas estão scrolladas para cima

    // --- Textos ---
    private final String placeholder;         // texto quando nada está seleccionado (ex: "Categoria")
    private final boolean enabled;            // desactivado = botão a cinzento, não abre popup
    private boolean active           = true;  // pode ser desactivado dinamicamente

    // --- Navegação por letra ---
    private char lastJumpLetter      = 0;     // última letra usada para jump
    private int  lastJumpIndex       = -1;    // índice em options do último jump (-1 = nenhum)

    // --- Drag da scroll bar ---
    private boolean isDragging       = false;
    private int     dragStartY       = 0;     // posição Y do rato quando iniciou o drag
    private int     dragStartOffset  = 0;     // scrollOffset quando iniciou o drag

    // --- Callback ---
    private final Consumer<String> onSelect;  // chamado quando o utilizador escolhe uma opção
                                              // null = limpar selecção (I18n.get("soundtweaks.gui.all"))

    // --- Referências ---
    private final Font font;

    public FilterDropdown(int x, int y, int width,
                          String placeholder,
                          Consumer<String> onSelect) {
        this.x           = x;
        this.y           = y;
        this.width       = width;
        this.placeholder = placeholder;
        this.onSelect    = onSelect;
        this.font        = Minecraft.getInstance().font;
        this.enabled     = true;
    }

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    /**
     * Actualiza as opções do dropdown.
     * Chamado quando a categoria muda (para o dropdown de objecto)
     * ou no init do ecrã (para o dropdown de categoria).
     *
     * @param options  valores internos (passados ao callback)
     * @param labels   labels para display (mesma ordem que options)
     */
    public void setOptions(List<String> options, List<String> labels) {
        this.options       = new ArrayList<>(options);
        this.labels        = new ArrayList<>(labels);
        this.scrollOffset  = 0;
        this.hoveredIndex  = -1;
        this.lastJumpLetter = 0;
        this.lastJumpIndex  = -1;
    }

    /** Limpa a selecção (volta ao estado "nada seleccionado"). */
    public void clearSelection() {
        this.selectedValue  = null;
        this.isOpen         = false;
        this.scrollOffset   = 0;
        this.lastJumpLetter = 0;
        this.lastJumpIndex  = -1;
    }

    /** Fecha a popup sem alterar a selecção. */
    public void close() {
        this.isOpen         = false;
        this.hoveredIndex   = -1;
        this.lastJumpLetter = 0;
        this.lastJumpIndex  = -1;
    }

    /** Activa ou desactiva o dropdown (ex: desactivar "Objecto" enquanto não há categoria). */
    public void setActive(boolean active) {
        this.active = active;
        if (!active) close();
    }

    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }

    public String getSelectedValue() { return selectedValue; }
    public boolean isOpen()          { return isOpen; }

    /** Restaura a selecção sem disparar o callback — usado para persistir estado entre sessões. */
    public void setSelectedValueSilently(@Nullable String value) {
        this.selectedValue = value;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /**
     * Desenha o botão e, se aberto, a popup por cima.
     * Deve ser chamado APÓS todos os outros widgets do ecrã.
     */
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        renderButton(graphics, mouseX, mouseY);
        if (isOpen) {
            renderPopup(graphics, mouseX, mouseY);
        }
    }

    private void renderButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean hovered = isMouseOverButton(mouseX, mouseY);

        // Fundo do botão
        int bgColor = !active          ? 0xFF333333  // desactivado — cinzento escuro
                    : hovered || isOpen ? 0xFF555566  // hover/aberto — azulado
                    :                     0xFF444444; // normal
        graphics.fill(x, y, x + width, y + BUTTON_HEIGHT, bgColor);

        // Borda
        int borderColor = active ? 0xFF888888 : 0xFF555555;
        graphics.fill(x,             y,                      x + width, y + 1,             borderColor); // topo
        graphics.fill(x,             y + BUTTON_HEIGHT - 1,  x + width, y + BUTTON_HEIGHT, borderColor); // fundo
        graphics.fill(x,             y,                      x + 1,     y + BUTTON_HEIGHT, borderColor); // esq
        graphics.fill(x + width - 1, y,                      x + width, y + BUTTON_HEIGHT, borderColor); // dir

        // Label: valor seleccionado ou placeholder
        String label = selectedValue != null
                ? getLabelForValue(selectedValue)
                : placeholder;
        int textColor = active ? 0xFFFFFFFF : 0xFF777777;

        // Truncar label se não couber (deixar espaço para "▾")
        String truncated = truncateToWidth(label, width - 18);
        graphics.text(font, truncated, x + 5, y + 6, textColor);

        // Seta ▾ (ou ▴ se aberto)
        String arrow = isOpen ? "▴" : "▾"; // ▴ ou ▾
        graphics.text(font, arrow, x + width - 11, y + 6, textColor);
    }

    private void renderPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int popupHeight = getPopupHeight();
        int popupY      = y + BUTTON_HEIGHT; // imediatamente abaixo do botão

        // Fundo da popup — ligeiramente mais escuro que o ecrã
        graphics.fill(x, popupY, x + width, popupY + popupHeight, 0xFF222222);

        // Borda da popup
        graphics.fill(x,             popupY,                  x + width, popupY + 1,          0xFF888888);
        graphics.fill(x,             popupY + popupHeight - 1, x + width, popupY + popupHeight, 0xFF888888);
        graphics.fill(x,             popupY,                  x + 1,     popupY + popupHeight, 0xFF888888);
        graphics.fill(x + width - 1, popupY,                  x + width, popupY + popupHeight, 0xFF888888);

        // Linhas visíveis
        int visibleCount = Math.min(MAX_VISIBLE, options.size() + 1); // +1 para I18n.get("soundtweaks.gui.all")
        hoveredIndex = -1;

        for (int i = 0; i < visibleCount; i++) {
            int optionIndex = i + scrollOffset - 1; // -1 porque índice 0 = I18n.get("soundtweaks.gui.all")
            int itemY = popupY + POPUP_PADDING + i * ITEM_HEIGHT;

            boolean isHovered = mouseX >= x && mouseX < x + width
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

            // Determinar se esta linha é a seleccionada actualmente
            boolean isSelected;
            String lineLabel;
            if (optionIndex < 0) {
                // Linha I18n.get("soundtweaks.gui.all") — índice -1, sempre no topo (se scrollOffset = 0)
                isSelected = (selectedValue == null);
                lineLabel  = I18n.get("soundtweaks.gui.all");
            } else if (optionIndex < options.size()) {
                isSelected = options.get(optionIndex).equals(selectedValue);
                lineLabel  = labels.get(optionIndex);
            } else {
                break; // não há mais opções
            }

            if (isHovered) hoveredIndex = optionIndex;

            // Fundo da linha: hover > seleccionado > normal
            if (isHovered) {
                graphics.fill(x + 1, itemY, x + width - 1, itemY + ITEM_HEIGHT, 0xFF3355AA);
            } else if (isSelected) {
                graphics.fill(x + 1, itemY, x + width - 1, itemY + ITEM_HEIGHT, 0xFF334466);
            }

            // Texto da linha — margem direita maior quando há barra de scroll (9px barra + 3px gap)
            int totalOpts = options.size() + 1;
            int textMaxWidth = totalOpts > MAX_VISIBLE ? width - 18 : width - 10;
            String truncated = truncateToWidth(lineLabel, textMaxWidth);
            graphics.text(font, truncated, x + 5, itemY + 3, 0xFFFFFFFF);
        }

        // Barra de scroll (se houver mais opções que o espaço visível)
        // SCROLLBAR_W = 6px — largura suficiente para ser clicável
        int totalOptions = options.size() + 1;
        if (totalOptions > MAX_VISIBLE) {
            int scrollTrackH = popupHeight - 4;
            int scrollThumbH = Math.max(12, scrollTrackH * MAX_VISIBLE / totalOptions);
            int scrollThumbY = popupY + 2 + (scrollTrackH - scrollThumbH) * scrollOffset / Math.max(1, totalOptions - MAX_VISIBLE);

            // Track (fundo cinzento escuro)
            graphics.fill(x + width - 7, popupY + 2, x + width - 1, popupY + popupHeight - 2, 0xFF333333);
            // Thumb (cinzento claro)
            graphics.fill(x + width - 7, scrollThumbY, x + width - 1, scrollThumbY + scrollThumbH, 0xFF888888);
        }
    }

    // -------------------------------------------------------------------------
    // Eventos de rato
    // -------------------------------------------------------------------------

    /**
     * Processa clique do rato.
     * @return true se o evento foi consumido (a UI normal não o deve processar)
     */
    public boolean mouseClicked(MouseButtonEvent event) {
        if (!active) return false;

        // Extrair coordenadas do evento (API 26.1.2 usa record MouseButtonEvent)
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        // Clique no botão — toggle popup
        if (isMouseOverButton(mouseX, mouseY)) {
            isOpen = !isOpen;
            if (isOpen) {
                scrollOffset    = 0;
                lastJumpLetter  = 0;   // reset ao abrir — cada sessão começa do zero
                lastJumpIndex   = -1;
            }
            return true;
        }

        // Clique dentro da popup
        if (isOpen && isMouseOverPopup(mouseX, mouseY)) {
            int popupY      = y + BUTTON_HEIGHT;
            int popupHeight = getPopupHeight();
            int totalOptions = options.size() + 1;

            // Clique na scroll bar (coluna direita, 6px de largura) — iniciar drag
            if (totalOptions > MAX_VISIBLE && mouseX >= x + width - 7 && mouseX < x + width - 1) {
                isDragging      = true;
                dragStartY      = mouseY;
                dragStartOffset = scrollOffset;
                // Também salta para a posição clicada imediatamente
                int trackTop  = popupY + 2;
                int trackH    = popupHeight - 4;
                double ratio  = (double)(mouseY - trackTop) / trackH;
                int maxOffset = totalOptions - MAX_VISIBLE;
                scrollOffset  = (int) Math.max(0, Math.min(maxOffset, Math.round(ratio * maxOffset)));
                dragStartOffset = scrollOffset; // actualizar base do drag para a posição saltada
                return true;
            }

            // Clique numa linha de opção — seleccionar e fechar
            int relativeY   = mouseY - popupY - POPUP_PADDING;
            int clickedLine = relativeY / ITEM_HEIGHT;
            int optionIndex = clickedLine + scrollOffset - 1; // -1 = I18n.get("soundtweaks.gui.all")

            if (optionIndex < 0) {
                selectedValue = null;
                onSelect.accept(null);
            } else if (optionIndex < options.size()) {
                selectedValue = options.get(optionIndex);
                onSelect.accept(selectedValue);
            }

            isOpen = false;
            return true;
        }

        // Clique fora — fechar popup sem consumir o evento
        // (ex: o utilizador clicou num slider enquanto a popup estava aberta)
        if (isOpen) {
            isOpen = false;
        }

        return false;
    }

    /**
     * Arrasto do rato — só actua se estiver a arrastar a scroll bar.
     * Chamado pelo SoundTweaksScreen.mouseDragged.
     * @return true se consumido
     */
    public boolean mouseDragged(double mouseY) {
        if (!isDragging) return false;

        int totalOptions = options.size() + 1;
        int maxOffset    = Math.max(0, totalOptions - MAX_VISIBLE);
        if (maxOffset == 0) return true;

        int popupHeight  = getPopupHeight();
        int trackH       = popupHeight - 4;
        // Pixels por unidade de scrollOffset
        double pixelsPerUnit = (double) trackH / maxOffset;
        int delta = (int) Math.round((mouseY - dragStartY) / pixelsPerUnit);
        scrollOffset = Math.max(0, Math.min(maxOffset, dragStartOffset + delta));
        return true;
    }

    /** Para o drag da scroll bar. */
    public void mouseReleased() {
        isDragging = false;
    }

    /**
     * Navega para a próxima opção cuja label começa com a letra indicada.
     * - Primeira vez com a letra X → vai para a 1ª opção com X
     * - Segunda vez com a letra X → vai para a 2ª opção com X
     * - Quando chega ao fim das opções com X, volta à 1ª (cycling)
     * - Trocar de letra recomeça do início para a nova letra
     */
    public boolean jumpToLetter(char c) {
        if (!isOpen || labels.isEmpty()) return false;
        char upper = Character.toUpperCase(c);

        // Recolher todos os índices de opções que começam com esta letra
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            if (!label.isEmpty() && Character.toUpperCase(label.charAt(0)) == upper) {
                matches.add(i);
            }
        }
        if (matches.isEmpty()) return false;

        // Determinar qual o próximo índice a mostrar
        int nextMatchPos; // posição dentro de matches[]
        if (upper != lastJumpLetter) {
            // Nova letra — começar no início
            nextMatchPos = 0;
        } else {
            // Mesma letra — avançar para o seguinte, com wrap-around
            int currentMatchPos = matches.indexOf(lastJumpIndex);
            nextMatchPos = (currentMatchPos + 1) % matches.size();
        }

        int targetIndex = matches.get(nextMatchPos); // índice em options[]
        lastJumpLetter = upper;
        lastJumpIndex  = targetIndex;

        // Calcular scrollOffset para colocar a opção no topo da popup visível.
        // Quando scrollOffset=S, a linha 0 mostra optionIndex = S-1.
        // Para options[targetIndex] na linha 0: S = targetIndex + 1.
        int maxOffset = Math.max(0, options.size() + 1 - MAX_VISIBLE);
        scrollOffset  = Math.min(targetIndex + 1, maxOffset);
        return true;
    }

    /**
     * Scroll do rato — só actua se o cursor estiver sobre a popup aberta.
     * @return true se consumido
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isOpen) return false;
        if (!isMouseOverPopup((int) mouseX, (int) mouseY)) return false;

        int totalOptions = options.size() + 1; // +1 para I18n.get("soundtweaks.gui.all")
        int maxOffset = Math.max(0, totalOptions - MAX_VISIBLE);

        scrollOffset = (int) Math.max(0, Math.min(maxOffset, scrollOffset - scrollY));
        return true;
    }

    // -------------------------------------------------------------------------
    // Helpers internos
    // -------------------------------------------------------------------------

    private boolean isMouseOverButton(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    private boolean isMouseOverPopup(int mouseX, int mouseY) {
        if (!isOpen) return false;
        int popupY = y + BUTTON_HEIGHT;
        return mouseX >= x && mouseX < x + width
                && mouseY >= popupY && mouseY < popupY + getPopupHeight();
    }

    /** Altura total da popup em pixels. */
    private int getPopupHeight() {
        int totalOptions = options.size() + 1; // +1 para I18n.get("soundtweaks.gui.all")
        int visibleCount = Math.min(MAX_VISIBLE, totalOptions);
        return visibleCount * ITEM_HEIGHT + POPUP_PADDING * 2;
    }

    /** Devolve a label de display para um valor interno. */
    private String getLabelForValue(String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equals(value)) return labels.get(i);
        }
        return value; // fallback: mostrar o valor bruto
    }

    /** Trunca texto para caber numa largura máxima em pixels, adicionando "…" se necessário. */
    private String truncateToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        while (!text.isEmpty() && font.width(text) + ellipsisWidth > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
