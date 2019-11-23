package fxprocessmanager.process;

public class Process {
    private final String name;

    public Process(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Process name cannot be null");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
