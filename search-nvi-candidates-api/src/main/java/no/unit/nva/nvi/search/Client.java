package no.unit.nva.nvi.search;

import java.io.IOException;
import no.sikt.nva.nvi.common.model.NviCandidateIndexDocument;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public interface Client {

    SearchResponse<NviCandidateIndexDocument> search(Query query) throws IOException;
}
