package fxprocessmanager.process;

import java.util.Set;

public interface ProcessManagerWatcher {
     public void updated(Set<ProcessState> changes);
}
