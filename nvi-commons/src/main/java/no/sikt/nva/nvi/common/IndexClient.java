package no.sikt.nva.nvi.common;

import java.util.Set;

public interface IndexClient<T> {

    void addDocumentToIndex(T indexDocument);
}
