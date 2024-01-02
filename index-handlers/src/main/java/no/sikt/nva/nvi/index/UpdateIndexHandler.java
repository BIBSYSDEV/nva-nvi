package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.aws.S3StorageWriter.GZIP_ENDING;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateIndexHandler implements RequestHandler<SQSEvent, Void> {

    public static final String FAILED_TO_ADD_DOCUMENT_TO_INDEX = "Failed to add document to index: {}";
    public static final String INDEX_DLQ = "INDEX_DLQ";
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateIndexHandler.class);
    private static final String FAILED_TO_MAP_BODY_MESSAGE = "Failed to map body to PersistedIndexDocumentMessage: {}";
    private static final String FAILED_TO_FETCH_DOCUMENT_MESSAGE = "Failed to fetch document from S3: {}";
    private static final String ERROR_MESSAGE = "Error message: {}";
    private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
    private final OpenSearchClient openSearchClient;
    private final StorageReader<URI> storageReader;
    private final QueueClient queueClient;
    private final String dlqUrl;

    @JacocoGenerated
    public UpdateIndexHandler() {
        this(OpenSearchClient.defaultOpenSearchClient(),
             new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)), new NviQueueClient());
    }

    public UpdateIndexHandler(OpenSearchClient openSearchClient, StorageReader<URI> storageReader,
                              QueueClient queueClient) {
        this.openSearchClient = openSearchClient;
        this.storageReader = storageReader;
        this.queueClient = queueClient;
        this.dlqUrl = new Environment().readEnv(INDEX_DLQ);
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(this::extractDocumentUriFromBody)
            .filter(Objects::nonNull)
            .map(this::fetchDocument)
            .filter(Objects::nonNull)
            .forEach(this::addDocumentToIndex);
        return null;
    }

    private static IndexDocumentWithConsumptionAttributes parseBlob(String blob) {
        return attempt(
            () -> dtoObjectMapper.readValue(blob, IndexDocumentWithConsumptionAttributes.class)).orElseThrow();
    }

    private static UUID extractCandidateIdentifier(URI docuemntUri) {
        return UUID.fromString(removeGz(UriWrapper.fromUri(docuemntUri).getPath().getLastPathElement()));
    }

    private static String removeGz(String filename) {
        return filename.replace(GZIP_ENDING, "");
    }

    private static void logFailure(String message, String messageArgument, Exception failure) {
        LOGGER.error(message, messageArgument);
        LOGGER.error(ERROR_MESSAGE, failure.getMessage());
    }

    private URI extractDocumentUriFromBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, PersistedIndexDocumentMessage.class).documentUri()).orElse(
            failure -> {
                handleFailure(failure, body);
                return null;
            });
    }

    private void handleFailure(Failure<?> failure, String message, String messageArgument,
                               UUID candidateIdentifier) {
        logFailure(message, messageArgument, failure.getException());
        queueClient.sendMessage(messageArgument, dlqUrl, candidateIdentifier);
    }

    private void handleFailure(Failure<?> failure, String messageArgument) {
        logFailure(FAILED_TO_MAP_BODY_MESSAGE, messageArgument, failure.getException());
        queueClient.sendMessage(messageArgument, dlqUrl);
    }

    private void addDocumentToIndex(NviCandidateIndexDocument document) {
        attempt(() -> openSearchClient.addDocumentToIndex(document)).orElse(
            failure -> {
                handleFailure(failure, FAILED_TO_ADD_DOCUMENT_TO_INDEX, document.identifier().toString(),
                              document.identifier());
                return null;
            });
    }

    private NviCandidateIndexDocument fetchDocument(URI documentUri) {
        return attempt(() -> parseBlob(storageReader.read(documentUri)).indexDocument()).orElse(
            failure -> {
                handleFailure(failure, FAILED_TO_FETCH_DOCUMENT_MESSAGE, documentUri.toString(),
                              extractCandidateIdentifier(documentUri));
                return null;
            });
    }
}
