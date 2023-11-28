package no.sikt.nva.nvi.common.queue;

import java.util.Collection;

public interface QueueClient<T> {

    T sendMessage(String message, String queueUrl);

    T sendMessageBatch(Collection<String> messages, String queueUrl);
}
