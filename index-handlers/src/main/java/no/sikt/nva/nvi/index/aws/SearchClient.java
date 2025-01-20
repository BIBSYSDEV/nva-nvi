package no.sikt.nva.nvi.index.aws;

import java.io.IOException;
import java.util.UUID;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchResponse;

public interface SearchClient<T> {

  IndexResponse addDocumentToIndex(T indexDocument);

  DeleteResponse removeDocumentFromIndex(UUID identifier);

  SearchResponse<T> search(CandidateSearchParameters candidateSearchParameters) throws IOException;

  void deleteIndex() throws IOException;
}
