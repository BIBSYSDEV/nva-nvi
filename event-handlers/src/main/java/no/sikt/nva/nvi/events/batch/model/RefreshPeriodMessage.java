package no.sikt.nva.nvi.events.batch.model;

public record RefreshPeriodMessage(String year) implements BatchJobMessage {}
