package ru.fifth.horror.trigger;

/** Pure occupancy rules shared by runtime logic and unit tests. */
public final class TriggerOccupancyPolicy {
    private TriggerOccupancyPolicy() {}

    public static int minimum(int requested) {
        return Math.max(1, requested);
    }

    public static boolean enterCrossed(int previous, int current, int minimum) {
        int required = minimum(minimum);
        return previous < required && current >= required;
    }

    public static boolean stayEligible(int current, int minimum) {
        return current >= minimum(minimum);
    }

    public static boolean exitQualified(int previous, int current, int minimum) {
        return previous >= minimum(minimum) && current < previous;
    }
}
