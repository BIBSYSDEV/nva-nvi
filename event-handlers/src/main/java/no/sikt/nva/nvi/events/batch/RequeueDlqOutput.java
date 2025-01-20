package no.sikt.nva.nvi.events.batch;

import java.util.Set;

public record RequeueDlqOutput(Set<NviProcessMessageResult> messages, int failedBatchesCount) {}
