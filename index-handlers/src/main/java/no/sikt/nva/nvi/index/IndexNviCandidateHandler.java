package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator.generateDocument;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.aws.S3StorageReader;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexNviCandidateHandler implements RequestHandler<DynamodbEvent, Void> {

    public static final String DOCUMENT_ADDED_MESSAGE = "Document has been added/updated";
    public static final String DOCUMENT_REMOVED_MESSAGE = "Document has been removed";
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexNviCandidateHandler.class);
    private static final Environment ENVIRONMENT = new Environment();
    private static final String EXPANDED_RESOURCES_BUCKET = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final StorageReader<URI> storageReader;

    @JacocoGenerated
    public IndexNviCandidateHandler() {
        this(new S3StorageReader(EXPANDED_RESOURCES_BUCKET), defaultOpenSearchClient());
    }

    public IndexNviCandidateHandler(StorageReader<URI> storageReader,
                                    SearchClient<NviCandidateIndexDocument> openSearchClient) {
        this.storageReader = storageReader;
        this.openSearchClient = openSearchClient;
    }

    public Void handleRequest(DynamodbEvent event, Context context) {
        event.getRecords().forEach(this::updateIndex);
        return null;
    }

    protected OperationType getEventType(DynamodbStreamRecord record) {
        return OperationType.fromValue(record.getEventName());
    }

    //TODO: Implement when we have DynamoDbRecord
    private static URI extractBucketUri(DynamodbStreamRecord record) {
        return URI.create("example.com" + record.getEventName());
    }

    private void updateIndex(DynamodbStreamRecord record) {
        if (isInsert(record) || isModify(record)) {
            addDocumentToIndex(record);
        }
        if (isRemove(record)) {
            removeDocumentFromIndex(record);
        }
    }

    private boolean isRemove(DynamodbStreamRecord record) {
        return getEventType(record).equals(OperationType.REMOVE);
    }

    private boolean isModify(DynamodbStreamRecord record) {
        return getEventType(record).equals(OperationType.MODIFY);
    }

    private boolean isInsert(DynamodbStreamRecord record) {
        return getEventType(record).equals(OperationType.INSERT);
    }

    private void removeDocumentFromIndex(DynamodbStreamRecord record) {
        openSearchClient.removeDocumentFromIndex(toCandidate(record));
        LOGGER.info(DOCUMENT_REMOVED_MESSAGE);
    }

    private void addDocumentToIndex(DynamodbStreamRecord record) {
        var approvalAffiliations = extractAffiliationApprovals();

        attempt(() -> extractBucketUri(record)).map(storageReader::read)
                                               .map(string -> generateDocument(string, approvalAffiliations))
                                               .forEach(openSearchClient::addDocumentToIndex);

        LOGGER.info(DOCUMENT_ADDED_MESSAGE);
    }

    //TODO: Implement when we have DynamoDbRecord
    private List<String> extractAffiliationApprovals() {
        return List.of("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
    }

    //TODO::Have to map DynamoDbRecord to IndexDocument when we know how DynamoRecord looks like
    private NviCandidateIndexDocument toCandidate(DynamodbStreamRecord record) {
        var image = record.getDynamodb().getNewImage();
        return new NviCandidateIndexDocument.Builder().withIdentifier(image.toString()).build();
    }
}
