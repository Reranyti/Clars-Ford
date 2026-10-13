package com.lotusblight.overlay;

/**
 * The clock of the fight and its cutscenes. It is System.nanoTime() minus the time spent paused, so while the
 * "close the window?" question is up every scene stands still, and carries on from the same moment afterwards.
 */
final class PauseClock {
    private static long frozenAt = -1;
    private static long offset;

    private PauseClock() {}

    static synchronized long nano() {
        return (frozenAt >= 0 ? frozenAt : System.nanoTime()) - offset;
    }

    static synchronized void pause() {
        if (frozenAt < 0) frozenAt = System.nanoTime();
    }

    static synchronized void resume() {
        if (frozenAt >= 0) {
            offset += System.nanoTime() - frozenAt;
            frozenAt = -1;
        }
    }
}
