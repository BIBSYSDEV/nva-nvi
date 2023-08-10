package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.io.IOException;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.DeleteNviCandidateMessageBody;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    public static final String SUCCESSFULLY_REMOVED_MESSAGE = "Document with id has been removed from index: {}";
    public static final String FAILED_TO_REMOVE_MESSAGE = "Failed to remove document from index: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteNviCandidateHandler.class);
    public final SearchClient<NviCandidateIndexDocument> openSearchClient;

    @JacocoGenerated
    public DeleteNviCandidateHandler() {
        this(defaultOpenSearchClient());
    }

    public DeleteNviCandidateHandler(SearchClient<NviCandidateIndexDocument> indexingClient) {
        this.openSearchClient = indexingClient;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(DeleteNviCandidateHandler::parseBody)
            .map(this::toIndexDocument)
            .forEach(this::removeFromIndex);
        return null;
    }

    private static DeleteNviCandidateMessageBody parseBody(String body) {
        return attempt(() -> JsonUtils.dtoObjectMapper.readValue(body, DeleteNviCandidateMessageBody.class))
                   .orElseThrow();
    }

    private void removeFromIndex(NviCandidateIndexDocument document) {
        try {
            openSearchClient.removeDocumentFromIndex(document);
            LOGGER.info(SUCCESSFULLY_REMOVED_MESSAGE, document.identifier());
        } catch (IOException e) {
            LOGGER.info(FAILED_TO_REMOVE_MESSAGE, document.identifier());
            throw new RuntimeException(e);
        }
    }

    private NviCandidateIndexDocument toIndexDocument(DeleteNviCandidateMessageBody message) {
        return new NviCandidateIndexDocument.Builder()
                   .withIdentifier(message.publicationIdentifier())
                   .build();
    }
}
