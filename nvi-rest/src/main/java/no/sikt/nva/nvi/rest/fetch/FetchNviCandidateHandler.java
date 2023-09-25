package no.sikt.nva.nvi.rest.fetch;

import static java.net.HttpURLConnection.HTTP_OK;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.rest.model.CandidateResponse;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.utils.ExceptionMapper;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.JacocoGenerated;

public class FetchNviCandidateHandler extends ApiGatewayHandler<Void, CandidateResponse> {

    public static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
    private final NviService service;

    @JacocoGenerated
    public FetchNviCandidateHandler() {
        this(NviService.defaultNviService());
    }

    public FetchNviCandidateHandler(NviService service) {
        super(Void.class);
        this.service = service;
    }

    @Override
    protected CandidateResponse processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        return attempt(() -> requestInfo.getPathParameter(CANDIDATE_IDENTIFIER))
                   .map(UUID::fromString)
                   .map(service::findCandidateById)
                   .map(Optional::orElseThrow)
                   .map(CandidateResponse::fromCandidate)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, CandidateResponse output) {
        return HTTP_OK;
    }
}
