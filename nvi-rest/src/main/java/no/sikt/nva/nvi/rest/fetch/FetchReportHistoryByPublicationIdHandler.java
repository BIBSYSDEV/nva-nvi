package no.sikt.nva.nvi.rest.fetch;

import static java.net.HttpURLConnection.HTTP_OK;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;

public class FetchReportHistoryByPublicationIdHandler extends ApiGatewayHandler<Void, String> {

    public static final String CANDIDATE_PUBLICATION_ID = "candidatePublicationId";

    public FetchReportHistoryByPublicationIdHandler(CandidateRepository candidateRepository) {
        super(Void.class);
    }

    @Override
    protected void validateRequest(Void unused, RequestInfo requestInfo, Context context) throws ApiGatewayException {

    }

    @Override
    protected String processInput(Void unused, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        return null;
    }

    private URI getPublicationId(RequestInfo requestInfo) {
        return URI.create(
            URLDecoder.decode(requestInfo.getPathParameters().get(CANDIDATE_PUBLICATION_ID), StandardCharsets.UTF_8));
    }

    @Override
    protected Integer getSuccessStatusCode(Void unused, String o) {
        return HTTP_OK;
    }
}
