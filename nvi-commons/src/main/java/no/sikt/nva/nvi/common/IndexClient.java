package no.sikt.nva.nvi.common;

public interface IndexClient<T> {

    void addDocumentToIndex(T indexDocument);
    void deleteIndex();
}
