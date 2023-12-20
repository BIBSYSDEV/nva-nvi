package no.sikt.nva.nvi.index;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.util.Objects;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

////TODO: Rename to UpdateIndexHandler when the old UpdateIndexHandler is removed
public class UpdateIndexHandlerV2 implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateIndexHandlerV2.class);
    private static final String FAILED_TO_MAP_BODY_MESSAGE = "Failed to map body to PersistedIndexDocumentMessage: {}";
    private static final String FAILED_TO_FETCH_DOCUMENT_MESSAGE = "Failed to fetch document from S3: {}";
    private static final String ERROR_MESSAGE = "Error message: {}";
    private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
    private final OpenSearchClient openSearchClient;
    private final StorageReader<URI> storageReader;

    @JacocoGenerated
    public UpdateIndexHandlerV2() {
        this(OpenSearchClient.defaultOpenSearchClient(),
             new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)));
    }

    public UpdateIndexHandlerV2(OpenSearchClient openSearchClient, StorageReader<URI> storageReader) {
        this.openSearchClient = openSearchClient;
        this.storageReader = storageReader;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(UpdateIndexHandlerV2::extractDocumentUriFromBody)
            .filter(Objects::nonNull)
            .map(this::fetchDocument)
            .filter(Objects::nonNull)
            .forEach(openSearchClient::addDocumentToIndex);
        return null;
    }

    private static URI extractDocumentUriFromBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, PersistedIndexDocumentMessage.class).documentUri()).orElse(
            failure -> {
                handleFailure(failure, FAILED_TO_MAP_BODY_MESSAGE, body);
                return null;
            });
    }

    private static void handleFailure(Failure<?> failure, String message, String messageArgument) {
        LOGGER.error(message, messageArgument);
        LOGGER.error(ERROR_MESSAGE, failure.getException().getMessage());
        //TODO: Send message to DLQ
    }

    private static IndexDocumentWithConsumptionAttributes parseBlob(String blob) {
        return attempt(
            () -> dtoObjectMapper.readValue(blob, IndexDocumentWithConsumptionAttributes.class)).orElseThrow();
    }

    private NviCandidateIndexDocument fetchDocument(URI documentUri) {
        return attempt(
            () -> parseBlob(storageReader.read(documentUri)).indexDocument()).orElse(
            failure -> {
                handleFailure(failure, FAILED_TO_FETCH_DOCUMENT_MESSAGE, documentUri.toString());
                return null;
            });
    }
}
