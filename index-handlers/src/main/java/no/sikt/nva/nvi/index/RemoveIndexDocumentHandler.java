package no.sikt.nva.nvi.index;

import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import nva.commons.core.attempt.Failure;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveIndexDocumentHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveIndexDocumentHandler.class);
    private static final String FAILED_TO_REMOVE_DOCUMENT_MESSAGE = "Failed to remove document from index: {}";
    private static final String FAILED_TO_EXTRACT_IDENTIFIER_MESSAGE = "Failed to extract identifier from dynamodb "
                                                                      + "record: {}";
    private static final String FAILED_TO_PARSE_EVENT_MESSAGE = "Failed to map body to DynamodbStreamRecord: {}";
    private static final String ERROR_MESSAGE = "Error message: {}";
    private static final String IDENTIFIER = "identifier";
    private final OpenSearchClient openSearchClient;

    public RemoveIndexDocumentHandler(OpenSearchClient openSearchClient) {

        this.openSearchClient = openSearchClient;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords().stream()
            .map(SQSMessage::getBody)
            .map(this::mapToDynamoDbRecord)
            .filter(Objects::nonNull)
            .map(RemoveIndexDocumentHandler::extractIdentifier)
            .filter(Objects::nonNull)
            .forEach(identifier -> getRemoveDocumentFromIndex(identifier));
        return null;
    }

    private static UUID extractIdentifier(DynamodbStreamRecord record) {
        return attempt(() -> UUID.fromString(extractImageIdentifier(record)))
                   .orElse(failure -> {
                       handleFailure(failure, FAILED_TO_EXTRACT_IDENTIFIER_MESSAGE, record.toString());
                       return null;
                   });
    }

    private static String extractImageIdentifier(DynamodbStreamRecord record) {
        return Optional.ofNullable(record.getDynamodb().getOldImage())
                   .orElse(record.getDynamodb().getNewImage())
                   .get(IDENTIFIER).getS();
    }

    private static void handleFailure(Failure<?> failure, String message, String messageArgument) {
        LOGGER.error(message, messageArgument);
        LOGGER.error(ERROR_MESSAGE, failure.getException().getMessage());
        //TODO: Send message to DLQ
    }

    private DeleteResponse getRemoveDocumentFromIndex(UUID identifier) {
        return attempt(() -> openSearchClient.removeDocumentFromIndex(identifier)).orElse(failure -> {
            handleFailure(failure, FAILED_TO_REMOVE_DOCUMENT_MESSAGE, identifier.toString());
            return null;
        });
    }

    private DynamodbStreamRecord mapToDynamoDbRecord(String body) {
        return attempt(() -> dynamoObjectMapper.readValue(body, DynamodbStreamRecord.class))
                   .orElse(failure -> {
                       handleFailure(failure, FAILED_TO_PARSE_EVENT_MESSAGE, body);
                       return null;
                   });
    }
}
