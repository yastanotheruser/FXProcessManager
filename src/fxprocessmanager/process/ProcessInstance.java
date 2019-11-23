package fxprocessmanager.process;

public class ProcessInstance {
    private Process process;
    private final int pid;
    private final ProcessPriority priority;
    private final int memoryUsage;
    private final int processTime;
    private final long hash;
    public final ProcessInfo info;

    public ProcessInstance(Process process, int pid, ProcessPriority priority, int memoryUsage, int processTime) {
        if (process == null) {
            throw new IllegalArgumentException("Process cannot be null");
        }
        if (pid < 1 || pid > 0xffff) {
            throw new IllegalArgumentException("PID must be a value 1 and 65535");
        }
        if (priority == null) {
            throw new IllegalArgumentException("Process priority cannot be null");
        }
        if (memoryUsage < 100 || memoryUsage > 300) {
            throw new IllegalArgumentException("Process memory usage must be a value between 100 and 300");
        }
        if (processTime < 10 || processTime > 50) {
            throw new IllegalArgumentException("Process time must be a value between 10 and 50");
        }

        this.pid = pid;
        this.process = process;
        this.priority = priority;
        this.memoryUsage = memoryUsage;
        this.processTime = processTime;
        this.hash = (priority.ordinal() << 22) | ((50 - processTime) << 16) | (0xffff - pid);
        this.info = new ProcessInfo(this);
    }

    public Process getProcess() {
        return process;
    }

    public int getPID() {
        return pid;
    }

    public ProcessPriority getPriority() {
        return priority;
    }

    public int getMemoryUsage() {
        return memoryUsage;
    }

    public int getProcessTime() {
        return processTime;
    }

    public long getHash() {
        return hash;
    }
}
