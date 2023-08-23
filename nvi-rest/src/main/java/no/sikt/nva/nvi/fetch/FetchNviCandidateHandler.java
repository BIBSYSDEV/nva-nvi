package no.sikt.nva.nvi.fetch;

import static java.net.HttpURLConnection.HTTP_OK;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.util.Optional;
import no.sikt.nva.nvi.common.service.NviService;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.Environment;

public class FetchNviCandidateHandler extends ApiGatewayHandler<Void, FetchCandidateResponse> {

    public static final String PARAM_CANDIDATE_IDENTIFIER = "candidateIdentifier";
    private final NviService service;

    public FetchNviCandidateHandler(NviService service, Environment environment) {
        super(Void.class, environment);
        this.service = service;
    }

    @Override
    protected FetchCandidateResponse processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        return attempt(() -> requestInfo.getPathParameter(PARAM_CANDIDATE_IDENTIFIER))
                   .map(URI::create)
                   .map(service::getCandidate)
                   .map(Optional::orElseThrow)
                   .map(FetchCandidateResponse::fromCandidate)
                   .orElseThrow((failure) -> new NotFoundException(
                       "No candidate found. " + failure.getException().getMessage()));
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, FetchCandidateResponse output) {
        return HTTP_OK;
    }
}
