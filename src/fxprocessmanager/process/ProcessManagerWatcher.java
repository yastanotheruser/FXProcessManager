package fxprocessmanager.process;

import java.util.ArrayList;

public interface ProcessManagerWatcher {
     public void updated(ArrayList<ProcessState> changes);
}
