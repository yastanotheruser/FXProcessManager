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
    public static final Comparator<ProcessInstance> hashComparator = Comparator.comparing(ProcessInstance::getHash).reversed();
    private final ArrayList<ProcessInstance> instances;
    public final Map<ProcessState, Collection<ProcessInstance>> collections;
    private final Set<ProcessManagerWatcher> watchers;
    private final ArrayList<ProcessInstance> inactiveList;
    private final ArrayList<ProcessInstance> suspendedList;
    private final PriorityQueue<ProcessInstance> readyQueue;
    private final Timer timer;
    private final TimerTask tick;
    private int processCount;
    private ProcessInstance executingInstance;
    private ProcessInstance highestPriorityInstance;
    private int delta;
    private Long tickInterval;

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
        processCount = 0;
        executingInstance = null;
        highestPriorityInstance = null;
        timer = new Timer();
        ProcessManager that = this;
        tick = new TimerTask() {
            @Override
            public void run() {
                if (instances.isEmpty()) {
                    return;
                }

                Set<ProcessState> changes = new HashSet();
                ProcessInstance next = null;
                if (highestPriorityInstance != null) {
                    if (highestPriorityInstance.info.getState() == ProcessState.INACTIVE) {
                        next = highestPriorityInstance;
                    }
                } else {
                    next = readyQueue.poll();
                }
                int inactiveSize = inactiveList.size();
                if (inactiveSize > 0) {
                    ProcessInstance[] inactiveArr = new ProcessInstance[inactiveSize];
                    inactiveList.toArray(inactiveArr);
                    for (ProcessInstance pi : inactiveArr) {
                        if (pi == highestPriorityInstance && next == highestPriorityInstance) {
                            continue;
                        }
                        pi.info.setState(ProcessState.READY);
                        readyQueue.add(pi);
                    }
                    inactiveList.clear();
                    changes.add(ProcessState.INACTIVE);
                    changes.add(ProcessState.READY);
                }

                int suspendedSize = suspendedList.size();
                if (suspendedSize > 0) {
                    ProcessInstance[] suspendedArr = new ProcessInstance[suspendedSize];
                    suspendedList.toArray(suspendedArr);
                    for (ProcessInstance pi : suspendedArr) {
                        pi.info.setState(ProcessState.READY);
                        readyQueue.add(pi);
                    }
                    suspendedList.clear();
                    changes.add(ProcessState.SUSPENDED);
                    changes.add(ProcessState.READY);
                }

                if (executingInstance != null) {
                    ProcessInfo info = executingInstance.info;
                    info.perform(that.delta);
                    System.out.println(info.getExecuted());
                    if (info.getExecuted() < executingInstance.getProcessTime()) {
                        if (executingInstance != highestPriorityInstance) {
                            info.setState(ProcessState.SUSPENDED);
                            suspendedList.add(executingInstance);
                            changes.add(ProcessState.SUSPENDED);
                        }
                    } else {
                        instances.remove(executingInstance);
                        if (executingInstance == highestPriorityInstance) {
                            executingInstance = null;
                            highestPriorityInstance = null;
                        }
                    }
                    if (highestPriorityInstance == null) {
                        executingInstance = null;
                    }
                    changes.add(ProcessState.EXECUTING);
                }

                if (next != null) {
                    next.info.setState(ProcessState.EXECUTING);
                    executingInstance = next;
                    changes.add(ProcessState.EXECUTING);
                }

                if (changes.isEmpty()) {
                    return;
                }

                dispatchWatchers(changes);
            }
        };
        setTickInterval(tickInterval);
        this.delta = delta;
    }

    public ProcessManager(int deltaPerTick) {
        this(deltaPerTick, null);
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

    public void start(Process process, ProcessPriority priority, int memoryUsage, int processTime) {
        processCount++;
        boolean hasHighestPriority = priority == ProcessPriority.HIGHEST;
        if (hasHighestPriority && highestPriorityInstance != null) {
            priority = ProcessPriority.HIGH;
        }

        ProcessInstance instance = new ProcessInstance(process, processCount, priority, memoryUsage, processTime);
        if (hasHighestPriority && highestPriorityInstance == null) {
            highestPriorityInstance = instance;
        }
        instances.add(instance);
        inactiveList.add(instance);
        Set<ProcessState> changes = new HashSet<>();
        changes.add(ProcessState.INACTIVE);
        dispatchWatchers(changes);
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

    public void dispatchWatchers(Set<ProcessState> changes) {
        for (ProcessManagerWatcher w : watchers) {
            w.updated(changes);
        }
    }
}
