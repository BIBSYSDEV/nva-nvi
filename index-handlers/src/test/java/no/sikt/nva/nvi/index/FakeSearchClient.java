package no.sikt.nva.nvi.index;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchResponse;

public class FakeSearchClient implements SearchClient<NviCandidateIndexDocument> {

    private final List<NviCandidateIndexDocument> documents;

    public FakeSearchClient() {
        this.documents = new ArrayList<>();
    }

    @Override
    public IndexResponse addDocumentToIndex(NviCandidateIndexDocument indexDocument) {
        documents.add(indexDocument);
        return null;
    }

    @Override
    public DeleteResponse removeDocumentFromIndex(UUID identifier) {

        return null;
    }

    @Override
    public SearchResponse<NviCandidateIndexDocument> search(CandidateSearchParameters candidateSearchParameters) {
        return null;
    }

    @Override
    public void deleteIndex() {

    }

    public List<NviCandidateIndexDocument> getDocuments() {
        return documents;
    }
}
