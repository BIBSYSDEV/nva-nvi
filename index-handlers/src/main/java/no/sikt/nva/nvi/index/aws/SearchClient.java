package no.sikt.nva.nvi.index.aws;

import java.io.IOException;
import no.sikt.nva.nvi.index.model.CandidateSearchParameters;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchResponse;

public interface SearchClient<T> {

    IndexResponse addDocumentToIndex(T indexDocument);

    DeleteResponse removeDocumentFromIndex(T indexDocument);

    SearchResponse<T> search(CandidateSearchParameters candidateSearchParameters)
        throws IOException;

    void deleteIndex() throws IOException;

}
