package no.sikt.nva.nvi.evaluator;

public interface QueueClient<T> {

    T sendMessage(String message);
}
