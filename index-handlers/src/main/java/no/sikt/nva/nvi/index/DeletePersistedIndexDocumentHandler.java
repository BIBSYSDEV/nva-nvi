package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.utils.DynamoDbUtils.extractIdFromRecord;
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
import no.sikt.nva.nvi.common.utils.DynamoDbUtils;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.IndexDocumentWithConsumptionAttributes;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class DeletePersistedIndexDocumentHandler implements RequestHandler<SQSEvent, Void> {

    private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";

    private final StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter;

    @JacocoGenerated
    public DeletePersistedIndexDocumentHandler() {
        this(new S3StorageWriter(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)));
    }

    public DeletePersistedIndexDocumentHandler(StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter) {
        this.storageWriter = storageWriter;
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

    private void deletePersistedIndexDocument(UUID identifier) {
        try {
            storageWriter.delete(identifier);
        } catch (IOException e) {
            return;
        }
    }

    private UUID extractIdentifier(DynamodbStreamRecord dynamodbStreamRecord) {
        return extractIdFromRecord(dynamodbStreamRecord).orElseThrow();
    }

    private DynamodbStreamRecord mapToDynamodbStreamRecord(String body) {
        return attempt(() -> DynamoDbUtils.toDynamodbStreamRecord(body))
                   .orElseThrow();
    }
}
