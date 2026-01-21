package no.sikt.nva.nvi.events.batch.message;

public record RefreshPeriodMessage(String year) implements BatchJobMessage {}
