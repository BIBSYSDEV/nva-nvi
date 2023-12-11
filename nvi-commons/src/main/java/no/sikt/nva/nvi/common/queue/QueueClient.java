package no.sikt.nva.nvi.common.queue;

import java.util.Collection;
import java.util.UUID;

public interface QueueClient<T, R> {

    T sendMessage(String message, String queueUrl);

    T sendMessage(String message, String queueUrl, UUID candidateIdentifier);

    R sendMessageBatch(Collection<String> messages, String queueUrl);
}
