package ru.fifth.horror.video;

public final class VideoPlaybackPolicy {
    public static final int STATIC_TICKS = 30;
    public enum State { PREPARING, STATIC, PLAYING, ENDED, ERROR }
    private VideoPlaybackPolicy() {}

    public static State next(State current, boolean mediaReady, int staticTicks, long mediaPositionMicros, long durationMicros) {
        if (current == null) return State.ERROR;
        if (current == State.ERROR || current == State.ENDED) return current;
        if (current == State.PREPARING) return mediaReady ? State.STATIC : State.PREPARING;
        if (current == State.STATIC) {
            if (!mediaReady) return State.PREPARING;
            return staticTicks >= STATIC_TICKS ? State.PLAYING : State.STATIC;
        }
        return durationMicros > 0 && mediaPositionMicros >= durationMicros ? State.ENDED : State.PLAYING;
    }
}
