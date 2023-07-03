package no.unit.nva.nvi.search;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.IOException;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import org.opensearch.client.opensearch.core.SearchResponse;

public class SearchNviCandidatesHandler extends ApiGatewayHandler<I, SearchResponse<NviCandidateIndexDocument>> {

    private final Client openSearchClient;

    public SearchNviCandidatesHandler(Class<I> iclass, Client openSearchClient) {
        super(iclass);
        this.openSearchClient = openSearchClient;
    }

    @Override
    protected SearchResponse<NviCandidateIndexDocument> processInput(I input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        try {
            return openSearchClient.search();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Integer getSuccessStatusCode(I input, SearchResponse<NviCandidateIndexDocument> output) {
        return null;
    }
}

