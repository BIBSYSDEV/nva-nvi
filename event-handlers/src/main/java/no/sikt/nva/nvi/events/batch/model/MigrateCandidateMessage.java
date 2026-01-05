package no.sikt.nva.nvi.events.batch.model;

import java.util.UUID;

public record MigrateCandidateMessage(UUID candidateIdentifier) implements BatchJobMessage {}
