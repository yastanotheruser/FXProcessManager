package fxprocessmanager.process;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import javafx.application.Platform;

public final class ProcessManager {
    public static final Comparator<ProcessInstance> hashComparator = Comparator.comparing(ProcessInstance::getHash).reversed();
    private final ProcessManager self = this;
    private final ArrayList<ProcessInstance> instances;
    public final Map<ProcessState, Collection<ProcessInstance>> collections;
    private final Set<ProcessManagerWatcher> watchers;
    private final ArrayList<ProcessInstance> inactiveList;
    private final ArrayList<ProcessInstance> suspendedList;
    private final PriorityQueue<ProcessInstance> readyQueue;
    private final Set<ProcessInstance> pausedInstances;
    private final class ProcessManagerTimerTask extends TimerTask {
        @Override
        public void run() {
            nextTick();
        }
    }
    private Timer timer;
    private int processCount;
    private ProcessInstance executingInstance;
    private ProcessInstance highestPriorityInstance;
    private int delta;
    private Long tickInterval;
    private Set<ProcessState> changes;
    private boolean commitingChanges;

    public ProcessManager(int delta, Long tickInterval) {
        if (delta <= 0) {
            throw new IllegalArgumentException("Process manager delta per tick must be a positive integer");
        }
        if (tickInterval != null && tickInterval < 0) {
            throw new IllegalArgumentException("Invalid tick interval, expected a positive integer or a null value");
        }

        instances = new ArrayList<>();
        watchers = new HashSet<>();
        collections = new HashMap<>();
        inactiveList = new ArrayList<>();
        collections.put(ProcessState.INACTIVE, inactiveList);
        suspendedList = new ArrayList<>();
        collections.put(ProcessState.SUSPENDED, suspendedList);
        readyQueue = new PriorityQueue<>(hashComparator);
        collections.put(ProcessState.READY, readyQueue);
        pausedInstances = new HashSet<>();
        processCount = 0;
        executingInstance = null;
        highestPriorityInstance = null;
        this.delta = delta;
        timer = new Timer();
        ProcessManager that = this;
        setTickInterval(tickInterval);
        changes = new HashSet<>();
        commitingChanges = false;
    }

    public ProcessManager(int delta) {
        this(delta, null);
    }

    public ProcessInstance[] getInstances() {
        ProcessInstance[] instancesArray = new ProcessInstance[instances.size()];
        return instances.toArray(instancesArray);
    }

    public ProcessInstance getExecutingInstance() {
        return executingInstance;
    }

    public ProcessInstance getHighestPriorityInstance() {
        return highestPriorityInstance;
    }

    public ProcessInstance start(Process process, ProcessPriority priority, int memoryUsage, int processTime) {
        processCount++;
        ProcessInstance instance = new ProcessInstance(process, processCount, priority, memoryUsage, processTime);
        instances.add(instance);
        inactiveList.add(instance);
        Set<ProcessState> changes = new HashSet<>();
        pushChanges(ProcessState.INACTIVE);
        return instance;
    }

    public void stop(ProcessInstance instance) {
        if (!instances.remove(instance)) {
            return;
        }

        if (executingInstance == instance) {
            executingInstance = null;
            pushChanges(ProcessState.EXECUTING);
        } else {
            ProcessState state = instance.info.getState();
            collections.get(state).remove(instance);
            pushChanges(state);
        }

        if (highestPriorityInstance == instance) {
            highestPriorityInstance = null;
        }

        if (isPaused(instance)) {
            pausedInstances.remove(instance);
        }
    }

    public void pause(ProcessInstance instance) {
        ProcessInfo info = instance.info;
        if (executingInstance != instance) {
            ProcessState state = info.getState();
            collections.get(state).remove(instance);
            pushChanges(state);
        } else {
            executingInstance = null;
            pushChanges(ProcessState.EXECUTING);
        }
        info.setState(ProcessState.INACTIVE);
        inactiveList.add(instance);
        pushChanges(ProcessState.INACTIVE);
        pausedInstances.add(instance);
    }

    public boolean resume(ProcessInstance instance) {
        if (!pausedInstances.remove(instance)) {
            return false;
        }

        pushChanges(instance.info.getState());
        return true;
    }

    public boolean isPaused(ProcessInstance instance) {
        return pausedInstances.contains(instance);
    }

    public void nextTick() {
        if (instances.isEmpty()) {
            return;
        }

        ProcessInstance next = null;
        if (highestPriorityInstance == null) {
            next = readyQueue.poll();
            if (next != null) {
                pushChanges(ProcessState.READY);
                if (next.getPriority() == ProcessPriority.HIGHEST) {
                    highestPriorityInstance = next;
                }
            }
        }

        int inactiveSize = inactiveList.size();
        if (inactiveSize > 0) {
            ProcessInstance[] inactiveArr = new ProcessInstance[inactiveSize];
            inactiveList.toArray(inactiveArr);
            for (ProcessInstance pi : inactiveArr) {
                if (isPaused(pi)) {
                    continue;
                }
                pi.info.setState(ProcessState.READY);
                readyQueue.add(pi);
            }
            inactiveList.clear();
            inactiveList.addAll(pausedInstances);
            pushChanges(ProcessState.INACTIVE);
            pushChanges(ProcessState.READY);
        }

        int suspendedSize = suspendedList.size();
        if (suspendedSize > 0) {
            boolean didChangeReady = false;
            ProcessInstance[] suspendedArr = new ProcessInstance[suspendedSize];
            suspendedList.toArray(suspendedArr);
            for (ProcessInstance pi : suspendedArr) {
                ProcessInfo info = pi.info;
                if (info.isReading()) {
                    info.setReadState(false);
                    continue;
                }

                info.setState(ProcessState.READY);
                suspendedList.remove(pi);
                readyQueue.add(pi);
                if (!didChangeReady) {
                    didChangeReady = true;
                }
            }

            pushChanges(ProcessState.SUSPENDED);
            if (didChangeReady) {
                pushChanges(ProcessState.READY);
            }
        }

        if (executingInstance != null) {
            ProcessInfo info = executingInstance.info;
            if (!info.isReading() || info.getExecuted() == 0) {
                info.perform(self.delta);
            } else {
                info.setReadState(false);
            }
            if (info.getExecuted() < executingInstance.getProcessTime()) {
                if (executingInstance != highestPriorityInstance) {
                    info.setState(ProcessState.SUSPENDED);
                    suspendedList.add(executingInstance);
                    pushChanges(ProcessState.SUSPENDED);
                }
            } else if (!info.isReading()) {
                instances.remove(executingInstance);
                if (executingInstance == highestPriorityInstance) {
                    highestPriorityInstance = null;
                }
            }
            if (highestPriorityInstance == null) {
                executingInstance = null;
            }
            pushChanges(ProcessState.EXECUTING);
        }

        if (next != null) {
            next.info.setState(ProcessState.EXECUTING);
            executingInstance = next;
            pushChanges(ProcessState.EXECUTING);
        }

        if (changes.isEmpty()) {
            return;
        }
    }

    public void setTickInterval(Long tickInterval) {
        if (this.tickInterval != null) {
            timer.cancel();
            timer.purge();
        }
        this.tickInterval = tickInterval;
        if (tickInterval != null) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new ProcessManagerTimerTask(), 0, tickInterval);
        }
    }

    public void setDelta(int delta) {
        this.delta = delta;
    }

    public void watch(ProcessManagerWatcher watcher) {
        watchers.add(watcher);
    }

    public void unwatch(ProcessManagerWatcher watcher) {
        watchers.remove(watcher);
    }

    public void destroy() {
        instances.clear();
        collections.clear();
        watchers.clear();
        inactiveList.clear();
        suspendedList.clear();
        readyQueue.clear();
        timer.cancel();
        timer.purge();
        processCount = 0;
        executingInstance = null;
        tickInterval = null;
    }

    private void pushChanges(ProcessState state) {
        if (!changes.contains(state)) {
            changes.add(state);
        }
        if (commitingChanges) {
            return;
        }

        commitingChanges = true;
        Platform.runLater(() -> {
            dispatchWatchers();
            commitingChanges = false;
        });
    }

    private void dispatchWatchers() {
        watchers.forEach(w -> w.updated(changes));
        changes.clear();
    }
}
