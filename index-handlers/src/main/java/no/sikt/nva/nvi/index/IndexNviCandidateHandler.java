package no.sikt.nva.nvi.index;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator.generateNviCandidateIndexDocument;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.Record;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.aws.S3StorageReader;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.NviCandidateMessageBody;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexNviCandidateHandler implements RequestHandler<DynamodbEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexNviCandidateHandler.class);
    private static final Environment ENVIRONMENT = new Environment();
    private static final String EXPANDED_RESOURCES_BUCKET = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
    private static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid: {}";
    private final SearchClient<NviCandidateIndexDocument> indexClient;
    private final StorageReader<NviCandidateMessageBody> storageReader;

    @JacocoGenerated
    public IndexNviCandidateHandler() {
        this(new S3StorageReader(EXPANDED_RESOURCES_BUCKET), defaultOpenSearchClient());
    }

    public IndexNviCandidateHandler(StorageReader<NviCandidateMessageBody> storageReader,
                                    SearchClient<NviCandidateIndexDocument> indexClient) {
        this.storageReader = storageReader;
        this.indexClient = indexClient;
    }

    public Void handleRequest(DynamodbEvent event, Context context) {
        var list =event.getRecords().stream()
             .map(attempt(JsonUtils.dtoObjectMapper::writeValueAsString))
            .toList();

        return null;
    }

    private void addNviCandidateToIndex(NviCandidateMessageBody candidate) {
        var indexedResource = storageReader.read(candidate);
        var indexDocument = generateNviCandidateIndexDocument(indexedResource, candidate);
        indexClient.addDocumentToIndex(indexDocument);
    }

    private NviCandidateMessageBody validate(NviCandidateMessageBody nviCandidate) {
        if (isNull(nviCandidate.publicationBucketUri())) {
            logInvalidMessageBody(nviCandidate.toString());
            return null;
        }
        return nviCandidate;
    }

    private NviCandidateMessageBody parseBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, NviCandidateMessageBody.class)).orElse(failure -> {
            logInvalidMessageBody(body);
            return null;
        });
    }

    private void logInvalidMessageBody(String body) {
        LOGGER.error(ERROR_MESSAGE_BODY_INVALID, body);
    }
}
