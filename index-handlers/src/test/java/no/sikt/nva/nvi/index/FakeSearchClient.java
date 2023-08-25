package no.sikt.nva.nvi.index;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public class FakeSearchClient implements SearchClient<NviCandidateIndexDocument> {

    private final Map<String, NviCandidateIndexDocument> indexContents;

    public FakeSearchClient() {
        indexContents = new ConcurrentHashMap<>();
    }

    @Override
    public void addDocumentToIndex(NviCandidateIndexDocument indexDocument) {
        indexContents.put(indexDocument.identifier(), indexDocument);
    }

    @Override
    public void removeDocumentFromIndex(NviCandidateIndexDocument indexDocument) {

    }

    @Override
    public SearchResponse<NviCandidateIndexDocument> search(Query query) {
        return null;
    }

    @Override
    public void deleteIndex() {

    }

    public Set<NviCandidateIndexDocument> listAllDocuments() {
        return new HashSet<>(this.indexContents.values());
    }
}
