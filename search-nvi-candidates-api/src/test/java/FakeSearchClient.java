import no.sikt.nva.nvi.common.model.NviCandidateIndexDocument;
import no.unit.nva.nvi.search.Client;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public class FakeSearchClient implements Client {

    @Override
    public SearchResponse<NviCandidateIndexDocument> search(Query query) {
        return null;
    }
}
