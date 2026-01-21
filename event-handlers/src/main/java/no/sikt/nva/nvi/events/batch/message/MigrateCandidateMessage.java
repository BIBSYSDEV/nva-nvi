package no.sikt.nva.nvi.events.batch.message;

import java.util.UUID;

public record MigrateCandidateMessage(UUID candidateIdentifier) implements BatchJobMessage {}
