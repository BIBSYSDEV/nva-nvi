package no.sikt.nva.nvi.common.queue;

import java.util.UUID;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public record NviCandidateUpdatedMessage(UUID candidateIdentifier, OperationType operationType,
                                         DataEntryType entryType) {}
