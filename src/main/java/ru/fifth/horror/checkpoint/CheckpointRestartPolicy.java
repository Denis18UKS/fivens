package ru.fifth.horror.checkpoint;

/** Small thread-safe guard that prevents two Fiven checkpoint restores from running at once. */
public final class CheckpointRestartPolicy {
    private boolean restarting;

    public synchronized boolean tryBegin() {
        if (restarting) return false;
        restarting = true;
        return true;
    }

    public synchronized void finish() {
        restarting = false;
    }

    public synchronized boolean isRestarting() {
        return restarting;
    }
}
