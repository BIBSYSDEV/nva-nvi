package no.sikt.nva.nvi.evaluator;

public interface StorageReader<T> {

    String read(T blob);
}
