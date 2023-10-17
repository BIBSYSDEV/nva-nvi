package no.sikt.nva.nvi.common.queue;

public interface QueueClient<T> {

    T sendMessage(String message, String queueUrl);
}
