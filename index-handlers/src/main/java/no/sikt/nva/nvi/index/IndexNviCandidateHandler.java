package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.ApplicationConstants.OPENSEARCH_ENDPOINT;
import static no.sikt.nva.nvi.common.ApplicationConstants.REGION;
import static no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator.generateNviCandidateIndexDocument;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.io.IOException;
import java.util.Objects;
import no.sikt.nva.nvi.common.IndexClient;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.aws.OpenSearchIndexClient;
import no.sikt.nva.nvi.index.aws.S3StorageReader;
import no.sikt.nva.nvi.index.model.NviCandidate;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexNviCandidateHandler.class);
    private static final String EXPANDED_RESOURCES_BUCKET = new Environment().readEnv(
        "EXPANDED_RESOURCES_BUCKET");
    private static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid: {}";
    private final IndexClient<NviCandidateIndexDocument> indexClient;
    private final StorageReader<NviCandidate> storageReader;

    @JacocoGenerated
    public IndexNviCandidateHandler() throws IOException {
        this.storageReader = new S3StorageReader(EXPANDED_RESOURCES_BUCKET);
        this.indexClient = new OpenSearchIndexClient(OPENSEARCH_ENDPOINT, REGION);
    }

    public IndexNviCandidateHandler(StorageReader<NviCandidate> storageReader,
                                    IndexClient<NviCandidateIndexDocument> indexClient) {
        this.storageReader = storageReader;
        this.indexClient = indexClient;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(this::parseBody)
            .filter(Objects::nonNull)
            .map(this::validate)
            .filter(Objects::nonNull)
            .forEach(this::addNviCandidateToIndex);

        return null;
    }

    private void addNviCandidateToIndex(NviCandidate candidate) {
        var indexedResource = storageReader.read(candidate);
        var indexDocument = generateNviCandidateIndexDocument(indexedResource, candidate);
        indexClient.addDocumentToIndex(indexDocument);
    }

    private NviCandidate validate(NviCandidate nviCandidate) {
        if(Objects.isNull(nviCandidate.publicationId())){
            logInvalidMessageBody(nviCandidate.toJsonString());
            return null;
        }
        return nviCandidate;
    }

    private NviCandidate parseBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, NviCandidate.class))
                   .orElse(failure -> {
                       logInvalidMessageBody(body);
                       return null;
                   });
    }

    private void logInvalidMessageBody(String body) {
        LOGGER.error(ERROR_MESSAGE_BODY_INVALID, body);
    }
}
