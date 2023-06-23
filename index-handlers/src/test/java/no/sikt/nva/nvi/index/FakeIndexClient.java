package no.sikt.nva.nvi.index;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import no.sikt.nva.nvi.common.IndexClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;

public class FakeIndexClient implements IndexClient<NviCandidateIndexDocument> {

    private final Map<String, NviCandidateIndexDocument> indexContents;

    public FakeIndexClient() {
        indexContents = new ConcurrentHashMap<>();
    }

    @Override
    public void addDocumentToIndex(NviCandidateIndexDocument indexDocument) {
        indexContents.put(indexDocument.identifier(), indexDocument);
    }

    @Override
    public Set<NviCandidateIndexDocument> listAllDocuments(String indexName) {
        return new HashSet<>(this.indexContents.values());
    }
}
