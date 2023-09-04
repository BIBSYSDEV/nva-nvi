package no.sikt.nva.nvi.index.aws;

import java.io.IOException;
import java.net.URI;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public interface SearchClient<T> {

    void addDocumentToIndex(T indexDocument);

    void removeDocumentFromIndex(T indexDocument);

    SearchResponse<T> search(Query query, String username, URI customer) throws IOException;

    void deleteIndex() throws IOException;
}
