package at.gastroflow.backend.demo.model;

public enum EmployeeStatus {

    IM_DIENST("Im Dienst"),
    PAUSE("Pause"),
    NICHT_ERSCHIENEN("Noch nicht erschienen"),
    FEIERABEND("Feierabend");

    private final String label;

    EmployeeStatus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
