package no.sikt.nva.nvi.events.batch;

public record RequeueDlqInput(int count) {
    public RequeueDlqInput() {
        this(10);
    }
}
