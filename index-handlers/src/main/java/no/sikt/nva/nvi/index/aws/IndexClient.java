package no.sikt.nva.nvi.index.aws;

public interface IndexClient<T> {

    void addDocumentToIndex(T indexDocument);
}
