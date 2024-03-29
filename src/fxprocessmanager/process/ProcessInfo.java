package fxprocessmanager.process;

import java.util.Date;

public class ProcessInfo {
    private ProcessInstance instance;
    private ProcessState state;
    private int executed;
    private Date lastExecuted;
    private boolean reading;

    public ProcessInfo(ProcessInstance instance, ProcessState state, int executed, boolean reading) {
        this.state = state;
        this.instance = instance;
        this.executed = executed;
        this.lastExecuted = null;
        this.reading = reading;
    }

    public ProcessInfo(ProcessInstance instance, ProcessState state) {
        this(instance, state, 0, false);
    }

    public ProcessInfo(ProcessInstance instance) {
        this(instance, ProcessState.INACTIVE);
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    public int getExecuted() {
        return executed;
    }

    public boolean isTerminated() {
        return executed == instance.getProcessTime();
    }

    public void perform(int delta) {
        int result = executed + delta;
        int processTime = instance.getProcessTime();
        if (result < 0) {
            result = 0;
        } else if (result > processTime) {
            result = processTime;
        }

        if (result != executed) {
            executed = result;
            lastExecuted = new Date();
        }
    }

    public boolean isReading() {
        return reading;
    }

    public void setReadState(boolean reading) {
        this.reading = reading;
    }

    public Date getLastExecuted() {
        return lastExecuted;
    }
}
