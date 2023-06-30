package no.sikt.nva.nvi.common;

public interface QueueClient<T> {

    T sendMessage(String message);
}
