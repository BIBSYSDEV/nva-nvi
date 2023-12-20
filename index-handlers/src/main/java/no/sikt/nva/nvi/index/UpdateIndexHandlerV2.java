package no.sikt.nva.nvi.index;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class UpdateIndexHandlerV2 implements RequestHandler<SQSEvent, Void> {

    public static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
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
            .map(UpdateIndexHandlerV2::parseBody)
            .map(this::fetchDocument)
            .forEach(openSearchClient::addDocumentToIndex);
        return null;
    }

    private static PersistedIndexDocumentMessage parseBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, PersistedIndexDocumentMessage.class)).orElseThrow();
    }

    private NviCandidateIndexDocument fetchDocument(
        PersistedIndexDocumentMessage persistedIndexDocumentMessage) {
        var blob = storageReader.read(persistedIndexDocumentMessage.documentUri());
        var documentWithConsumptionAttributes = attempt(
            () -> dtoObjectMapper.readValue(blob, IndexDocumentWithConsumptionAttributes.class)).orElseThrow();
        return documentWithConsumptionAttributes.indexDocument();
    }
}
