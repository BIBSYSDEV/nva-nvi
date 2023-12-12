package no.sikt.nva.nvi.index.model;

public enum Operation {
    INSERT,
    MODIFY,
    REMOVE;
    public static Operation parse(String operationType) {
        return switch (operationType) {
            case "MODIFY" -> MODIFY;
            case "REMOVE" -> REMOVE;
            default -> INSERT;
        };
    }
}
