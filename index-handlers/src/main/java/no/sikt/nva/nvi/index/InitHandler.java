package no.sikt.nva.nvi.index;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitHandler implements RequestHandler<Object, String> {
    public static final String SUCCESS = "SUCCESS";
    private static final Logger LOGGER = LoggerFactory.getLogger(InitHandler.class);
    private final OpenSearchClient indexingClient;

    @JacocoGenerated
    public InitHandler() {
        this(OpenSearchClient.defaultOpenSearchClient());
    }

    public InitHandler(OpenSearchClient indexingClient) {
        this.indexingClient = indexingClient;
    }

    @Override
    public String handleRequest(Object input, Context context) {
        if (!this.indexingClient.indexExists()) {
            LOGGER.info("Creating index");
            this.indexingClient.createIndex();
        } else {
            LOGGER.info("Index already exists");
        }

        return SUCCESS;
    }
}
