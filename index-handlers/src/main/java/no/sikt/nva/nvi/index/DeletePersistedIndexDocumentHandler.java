package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.utils.DynamoDbUtils.extractIdFromRecord;
import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.utils.DynamoDbUtils;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.IndexDocumentWithConsumptionAttributes;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class DeletePersistedIndexDocumentHandler implements RequestHandler<SQSEvent, Void> {

    public static final String INDEX_DLQ = "INDEX_DLQ";
    private static final Logger LOGGER = LoggerFactory.getLogger(DeletePersistedIndexDocumentHandler.class);
    private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
    private static final String ERROR_MESSAGE = "Error message: {}";

    private static final String FAILED_TO_PARSE_EVENT_MESSAGE = "Failed to map body to DynamodbStreamRecord: {}";
    private final StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter;

    private final QueueClient queueClient;
    private final String dlqUrl;

    @JacocoGenerated
    public DeletePersistedIndexDocumentHandler() {
        this(new S3StorageWriter(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)), new NviQueueClient(),
             new Environment());
    }

    public DeletePersistedIndexDocumentHandler(StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter,
                                               QueueClient queueClient, Environment environment) {
        this.storageWriter = storageWriter;
        this.queueClient = queueClient;
        this.dlqUrl = environment.readEnv(INDEX_DLQ);
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords().stream()
            .map(SQSMessage::getBody)
            .map(this::mapToDynamodbStreamRecord)
            .filter(Objects::nonNull)
            .map(this::extractIdentifier)
            .filter(Objects::nonNull)
            .forEach(this::deletePersistedIndexDocument);
        return null;
    }

    private static void logFailure(String message, String messageArgument, Exception exception) {
        LOGGER.error(message, messageArgument);
        LOGGER.error(ERROR_MESSAGE, getStackTrace(exception));
    }

    private void deletePersistedIndexDocument(UUID identifier) {
        try {
            storageWriter.delete(identifier);
        } catch (S3Exception | IOException exception) {
            handleFailure(new Failure<>(exception), identifier.toString(),
                          identifier);
        }
    }

    private UUID extractIdentifier(DynamodbStreamRecord dynamodbStreamRecord) {
        return extractIdFromRecord(dynamodbStreamRecord).orElseGet(() -> {
            var message = String.format("Failed to extract identifier from record %s", dynamodbStreamRecord.toString());
            LOGGER.error(message);
            queueClient.sendMessage(message, dlqUrl);
            return null;
        });
    }

    private DynamodbStreamRecord mapToDynamodbStreamRecord(String body) {
        return attempt(() -> DynamoDbUtils.toDynamodbStreamRecord(body))
                   .orElse(failure -> {
                       handleFailure(failure, body);
                       return null;
                   });
    }

    private void handleFailure(Failure<?> failure, String messageArgument) {
        logFailure(FAILED_TO_PARSE_EVENT_MESSAGE, messageArgument, failure.getException());
        queueClient.sendMessage(failure.getException().getMessage(), dlqUrl);
    }

    private void handleFailure(Failure<?> failure, String messageArgument, UUID candidateIdentifier) {
        logFailure("Failed to delete file with identifier {}", messageArgument, failure.getException());
        queueClient.sendMessage(getStackTrace(failure.getException()), dlqUrl, candidateIdentifier);
    }
}
