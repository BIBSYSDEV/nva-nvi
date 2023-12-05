package no.sikt.nva.nvi.common.queue;

import java.util.Collection;

public interface QueueClient<T, R> {

    T sendMessage(String message, String queueUrl);

    R sendMessageBatch(Collection<String> messages, String queueUrl);
}
