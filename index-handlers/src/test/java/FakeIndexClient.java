import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import no.sikt.nva.nvi.common.IndexClient;
import no.sikt.nva.nvi.common.model.IndexDocument;

public class FakeIndexClient implements IndexClient {

    private final Map<String, Map<String, IndexDocument>> indexContents;

    public FakeIndexClient() {
        indexContents = new ConcurrentHashMap<>();
    }

    @Override
    public Set<IndexDocument> listAllDocuments(String indexName) {
        return new HashSet<>(this.indexContents.get(indexName).values());
    }
}
