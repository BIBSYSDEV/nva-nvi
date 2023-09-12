package no.sikt.nva.nvi.index.aws;

import java.io.IOException;
import java.net.URI;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import org.opensearch.client.opensearch.core.SearchResponse;

public interface SearchClient<T> {

    void addDocumentToIndex(T indexDocument);

    void removeDocumentFromIndex(T indexDocument);

    SearchResponse<T> search(String searchTerm, String filter, String username, URI customer, int offset, int size)
        throws IOException;

    void deleteIndex() throws IOException;

}
