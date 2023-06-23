package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator.generateNviCandidateIndexDocument;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Objects;
import no.sikt.nva.nvi.common.IndexClient;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.model.NviCandidate;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexNviCandidateHandler.class);
    private final IndexClient<NviCandidateIndexDocument> indexClient;

    private final StorageReader<NviCandidate> storageReader;

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
            .forEach(this::addNviCandidateToIndex);

        return null;
    }

    private void addNviCandidateToIndex(NviCandidate candidate) {
        var indexedResource = storageReader.read(candidate);
        indexClient.addDocumentToIndex(generateNviCandidateIndexDocument(indexedResource, candidate));
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
