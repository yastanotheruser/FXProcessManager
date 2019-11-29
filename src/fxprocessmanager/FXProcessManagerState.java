package fxprocessmanager;

import fxprocessmanager.process.Process;
import fxprocessmanager.process.ProcessManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FXProcessManagerState {
    private final File file;
    private final ArrayList<Process> processes;
    private final ProcessManager pm;

    public FXProcessManagerState(File file) {
        this.file = file;
        this.processes = new ArrayList<>();
        this.pm = new ProcessManager(5);
        try {
            this.loadData();
        } catch (IOException ex) {
            Logger.getLogger(FXProcessManagerState.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void loadData() throws IOException {
        try {
            FileInputStream fis = new FileInputStream(file);
            int header = -1;
            int b;

            while ((b = fis.read()) != -1) {
                if (header == -1) {
                    header = b;
                    continue;
                }

                if (header == 0x00) {
                    int count = ((int) b) << 8;
                    b = fis.read();
                    if (b == -1) {
                        this.throwInvalidFormatException();
                    }

                    count |= b;
                    for (int i = 0; i < count; i++) {
                        int length = fis.read();
                        if (length == -1) {
                            this.throwInvalidFormatException();
                        }

                        byte[] processNameBytes = new byte[length];
                        if (fis.read(processNameBytes) == -1) {
                            this.throwInvalidFormatException();
                        }

                        Process p = new Process(new String(processNameBytes));
                        processes.add(p);
                    }

                    header = -1;
                }
            }
        } catch (FileNotFoundException | IllegalArgumentException ex) {
            file.createNewFile();
        }
    }

    private void throwInvalidFormatException() throws IOException {
        throw new IOException("Invalid file format");
    }

    public void saveState() throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }

        try {
            int size = processes.size();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(0x00);
            fos.write((size >> 8) & 255);
            fos.write(size & 255);
            for (Process p : processes) {
                String name = p.getName();
                fos.write(name.length());
                fos.write(name.getBytes());
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(FXProcessManagerState.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public boolean addProcess(Process proc) {
        return this.processes.add(proc);
    }

    public boolean removeProcess(Process proc) {
        return this.processes.remove(proc);
    }

    public Process removeProcess(int index) {
        return this.processes.remove(index);
    }

    public Process[] getProcesses() {
        Process[] arr = new Process[processes.size()];
        return processes.toArray(arr);
    }

    public Process getProcess(int index) {
        return processes.get(index);
    }

    public int getProcessCount() {
        return processes.size();
    }

    public ProcessManager getProcessManager() {
        return pm;
    }
}
