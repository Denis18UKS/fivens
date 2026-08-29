package ru.fifth.horror.vhs;

/** Pure bounds policy for keyboard-driven VHS frame browsing. */
public final class VhsFrameNavigationPolicy {
    private VhsFrameNavigationPolicy() {}

    public static int move(int current, int delta, int frameCount) {
        if (frameCount <= 0) return 0;
        int safeCurrent = Math.max(0, Math.min(frameCount - 1, current));
        long next = (long) safeCurrent + delta;
        return (int) Math.max(0L, Math.min(frameCount - 1L, next));
    }
}
