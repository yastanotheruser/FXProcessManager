package fxprocessmanager.process;

public enum ProcessState {
    INACTIVE, READY, EXECUTING, SUSPENDED;

    public static final int count = values().length;
}
