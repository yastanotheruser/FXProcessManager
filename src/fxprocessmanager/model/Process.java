package fxprocessmanager.model;

public class Process {
    private final String name;
    private final ProcessPriority priority;
    private final int memoryUsage;
    private final int processTime;
    private int pid;
    private int processed;
    private boolean waitingInput;

    public Process(String name, ProcessPriority priority, int memoryUsage, int processTime) {
        if (name == null) {
            throw new IllegalArgumentException("Process name cannot be null");
        }
        this.name = name;
        if (priority == null) {
            throw new IllegalArgumentException("Process priority cannot be null");
        }
        this.priority = priority;
        if (memoryUsage < 100 || memoryUsage > 300) {
            throw new IllegalArgumentException("Process memory usage must be a value between 100 and 300");
        }
        this.memoryUsage = memoryUsage;
        if (processTime < 10 || processTime > 50) {
            throw new IllegalArgumentException("Process time must be a value between 10 and 50");
        }
        this.processTime = processTime;
    }

    public String getName() {
        return name;
    }

    public ProcessPriority getPriority() {
        return this.priority;
    }

    public int getMemoryUsage() {
        return this.memoryUsage;
    }

    public int getProcessTime() {
        return this.processTime;
    }

    public int getPID() {
        return pid;
    }

    public void setPID(int pid) {
        this.pid = pid;
    }

    public int getProcessed() {
        return processed;
    }

    public void perform(int delta) {
        processed += delta;
    }

    public boolean isReading() {
        return waitingInput;
    }

    public void setReadState(boolean reading) {
        waitingInput = reading;
    }
}
