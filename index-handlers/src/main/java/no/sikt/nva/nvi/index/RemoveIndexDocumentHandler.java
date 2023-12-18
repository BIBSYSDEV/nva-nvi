package no.sikt.nva.nvi.index;

import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveIndexDocumentHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveIndexDocumentHandler.class);

    private static final String ERROR_MESSAGE = "Error message: {}";
    private static final String FAILED_TO_PARSE_EVENT_MESSAGE = "Failed to map body to DynamodbStreamRecord: {}";
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
            .map(RemoveIndexDocumentHandler::extractIdentifier)
            .forEach(openSearchClient::removeDocumentFromIndex);
        return null;
    }

    private static UUID extractIdentifier(DynamodbStreamRecord record) {
        return UUID.fromString(Optional.ofNullable(record.getDynamodb().getOldImage())
                                   .orElse(record.getDynamodb().getNewImage())
                                   .get(IDENTIFIER).getS());
    }

    private DynamodbStreamRecord mapToDynamoDbRecord(String body) {
        return attempt(() -> dynamoObjectMapper.readValue(body, DynamodbStreamRecord.class))
                   .orElse(failure -> {
                       handleFailure(failure, FAILED_TO_PARSE_EVENT_MESSAGE, body);
                       return null;
                   });
    }

    private void handleFailure(Failure<?> failure, String message, String messageArgument) {
        LOGGER.error(message, messageArgument);
        LOGGER.error(ERROR_MESSAGE, failure.getException().getMessage());
        //TODO: Send message to DLQ
    }
}
