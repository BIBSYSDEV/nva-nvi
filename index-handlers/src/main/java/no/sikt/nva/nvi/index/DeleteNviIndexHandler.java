package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.io.IOException;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteNviIndexHandler implements RequestHandler<Object, String> {

    public static final String FINISHED = "FINISHED";
    public static final String INDEX_DELETION_FAILED_MESSAGE = "Index deletion failed";
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteNviIndexHandler.class);
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;

    @JacocoGenerated
    public DeleteNviIndexHandler() {
        this(defaultOpenSearchClient());
    }

    public DeleteNviIndexHandler(SearchClient<NviCandidateIndexDocument> indexingClient) {
        this.openSearchClient = indexingClient;
    }

    @Override
    public String handleRequest(Object input, Context context) {
        try {
            openSearchClient.deleteIndex();
        } catch (IOException e) {
            LOGGER.error(INDEX_DELETION_FAILED_MESSAGE);
            throw new RuntimeException(e);
        }
        LOGGER.info(FINISHED);
        return null;
    }
}
