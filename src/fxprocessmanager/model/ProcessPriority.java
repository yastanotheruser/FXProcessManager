package fxprocessmanager.model;

public enum ProcessPriority {
    LOW, NORMAL, HIGH, HIGHEST;

    private static final ProcessPriority[] cache = values();
    public static final int count = cache.length;

    public static ProcessPriority getValue(int index) {
        return cache[index];
    }
}
