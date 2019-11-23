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

public final class ProcessManager {
    private static final Comparator<ProcessInstance> hashComparator = Comparator.comparing(ProcessInstance::getHash).reversed();
    private final ArrayList<ProcessInstance> instances;
    public final Map<ProcessState, Collection<ProcessInstance>> lists;
    private final Set<ProcessManagerWatcher> watchers;
    private final ArrayList<ProcessInstance> inactiveList;
    private final ArrayList<ProcessInstance> suspendedList;
    private final PriorityQueue<ProcessInstance> readyQueue;
    private final Timer timer;
    private final TimerTask tick;
    private int processCount;
    private ProcessInstance executingInstance;
    private Long tickInterval;

    public ProcessManager(int deltaPerTick, Long tickInterval) {
        if (deltaPerTick <= 0) {
            throw new IllegalArgumentException("Process manager delta per tick must be a positive integer");
        }
        if (tickInterval != null && tickInterval < 0) {
            throw new IllegalArgumentException("Invalid tick interval, expected a positive integer or a null value");
        }

        instances = new ArrayList<>();
        watchers = new HashSet<>();
        lists = new HashMap<>();
        inactiveList = new ArrayList<>();
        lists.put(ProcessState.INACTIVE, inactiveList);
        suspendedList = new ArrayList<>();
        lists.put(ProcessState.SUSPENDED, suspendedList);
        readyQueue = new PriorityQueue<>(hashComparator);
        lists.put(ProcessState.READY, readyQueue);
        processCount = 0;
        executingInstance = null;
        timer = new Timer();
        tick = new TimerTask() {
            @Override
            public void run() {
                if (instances.isEmpty()) {
                    return;
                }

                ArrayList<ProcessState> changes = new ArrayList(ProcessState.count);
                ProcessInstance next = readyQueue.poll();
                int inactiveSize = inactiveList.size();
                if (inactiveSize > 0) {
                    ProcessInstance[] inactiveArr = new ProcessInstance[inactiveSize];
                    inactiveList.toArray(inactiveArr);
                    for (ProcessInstance pi : inactiveArr) {
                        pi.info.setState(ProcessState.READY);
                        readyQueue.add(pi);
                    }
                    inactiveList.clear();
                    changes.add(ProcessState.INACTIVE);
                }

                boolean suspendedHasChanges = false;
                int suspendedSize = inactiveList.size();
                if (suspendedSize > 0) {
                    ProcessInstance[] suspendedArr = new ProcessInstance[inactiveSize];
                    suspendedList.toArray(suspendedArr);
                    for (ProcessInstance pi : suspendedArr) {
                        pi.info.setState(ProcessState.READY);
                        readyQueue.add(pi);
                    }
                    suspendedList.clear();
                    changes.add(ProcessState.SUSPENDED);
                    suspendedHasChanges = true;
                }

                if (executingInstance != null) {
                    ProcessInfo info = executingInstance.info;
                    info.perform(deltaPerTick);
                    if (info.getExecuted() < executingInstance.getProcessTime()) {
                        info.setState(ProcessState.SUSPENDED);
                        suspendedList.add(executingInstance);
                        if (!suspendedHasChanges) {
                            changes.add(ProcessState.SUSPENDED);
                            suspendedHasChanges = true;
                        }
                    } else {
                        instances.remove(executingInstance);
                    }
                    changes.add(ProcessState.EXECUTING);
                }

                if (next != null) {
                    next.info.setState(ProcessState.EXECUTING);
                    executingInstance = next;
                }

                if (changes.isEmpty()) {
                    return;
                }

                for (ProcessManagerWatcher w : watchers) {
                    w.updated(changes);
                }
            }
        };
        setTickInterval(tickInterval);
    }

    public ProcessManager(int deltaPerTick) {
        this(deltaPerTick, null);
    }

    public ProcessInstance getExecutingInstance() {
        return executingInstance;
    }

    public void start(Process process, ProcessPriority priority, int memoryUsage, int processTime) {
        processCount++;
        ProcessInstance instance = new ProcessInstance(process, processCount, priority, memoryUsage, processTime);
        instances.add(instance);
        inactiveList.add(instance);
    }

    public void nextTick() {
        tick.run();
    }

    public void setTickInterval(Long tickInterval) {
        if (this.tickInterval != null) {
            timer.cancel();
            timer.purge();
        }
        this.tickInterval = tickInterval;
        if (tickInterval != null) {
            timer.scheduleAtFixedRate(tick, 0, tickInterval);
        }
    }

    public void watch(ProcessManagerWatcher watcher) {
        watchers.add(watcher);
    }

    public void unwatch(ProcessManagerWatcher watcher) {
        watchers.remove(watcher);
    }

    public void destroy() {
        instances.clear();
        lists.clear();
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
}
