package com.scr0ols.soundtweaks;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight hot-path profiler for SoundTweaks.
 *
 * Collects call counts and nanosecond timing for getVolume() invocations.
 * Thread-safe via AtomicLong — minimal overhead on the audio thread.
 * Reset + report triggered by keybind from the render thread.
 */
public class PerfStats {

    private static final AtomicLong totalCalls     = new AtomicLong();
    private static final AtomicLong modifiedCalls  = new AtomicLong(); // mult != 1.0
    private static final AtomicLong totalNanos      = new AtomicLong();
    private static volatile long    windowStartNs   = System.nanoTime();

    public static void recordCall(long elapsedNs, boolean modified) {
        totalCalls.incrementAndGet();
        totalNanos.addAndGet(elapsedNs);
        if (modified) modifiedCalls.incrementAndGet();
    }

    /** Returns a summary string and resets all counters. */
    public static String reportAndReset() {
        long calls    = totalCalls.getAndSet(0);
        long modified = modifiedCalls.getAndSet(0);
        long nanos    = totalNanos.getAndSet(0);
        long windowMs = (System.nanoTime() - windowStartNs) / 1_000_000L;
        windowStartNs = System.nanoTime();

        if (calls == 0) return "[SoundTweaks Perf] Sem chamadas registadas.";

        long avgNs  = nanos / calls;
        long cps    = windowMs > 0 ? (calls * 1000L / windowMs) : 0;

        return String.format(
            "[SoundTweaks Perf] %ds | %,d chamadas | %,d/s | avg %.2f µs | %,d modificadas (%.1f%%)",
            windowMs / 1000,
            calls,
            cps,
            avgNs / 1000.0,
            modified,
            calls > 0 ? (modified * 100.0 / calls) : 0.0
        );
    }
}
