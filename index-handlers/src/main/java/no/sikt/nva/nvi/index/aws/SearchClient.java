package no.sikt.nva.nvi.index.aws;

import java.io.IOException;
import no.sikt.nva.nvi.common.model.NviCandidateIndexDocument;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public interface SearchClient {

    SearchResponse<NviCandidateIndexDocument> search(Query query) throws IOException;
}
