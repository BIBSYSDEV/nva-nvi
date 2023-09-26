package no.sikt.nva.nvi.rest.upsert;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.model.CandidateResponse;
import no.sikt.nva.nvi.rest.model.CandidateResponseMapper;
import no.sikt.nva.nvi.utils.ExceptionMapper;
import no.sikt.nva.nvi.utils.RequestUtil;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

public class UpdateNviCandidateStatusHandler extends ApiGatewayHandler<NviStatusRequest, CandidateResponse> {

    private final NviService nviService;

    @JacocoGenerated
    public UpdateNviCandidateStatusHandler() {
        this(new NviService(defaultDynamoClient()));
    }

    public UpdateNviCandidateStatusHandler(NviService service) {
        super(NviStatusRequest.class);
        this.nviService = service;
    }

    @Override
    protected CandidateResponse processInput(NviStatusRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        validateRequest(input, requestInfo);
        var candidateIdentifier = UUID.fromString(requestInfo.getPathParameter(CANDIDATE_IDENTIFIER));
        var username = requestInfo.getUserName();

        return attempt(() -> toApprovalStatus(input, candidateIdentifier)).map(approval -> approval.fetch(nviService))
                   .map(approval -> approval.update(nviService, input.toUpdateRequest(username)))
                   .map(this::fetchCandidate)
                   .map(Optional::orElseThrow)
                   .map(CandidateResponseMapper::fromCandidate)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(NviStatusRequest input, CandidateResponse output) {
        return HTTP_OK;
    }

    private static void validateRequest(NviStatusRequest input, RequestInfo requestInfo)
        throws UnauthorizedException, ForbiddenException {
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATE);
        hasSameCustomer(requestInfo, input);
    }

    private static void hasSameCustomer(RequestInfo requestInfo, NviStatusRequest input) throws ForbiddenException {
        if (!input.institutionId().equals(requestInfo.getTopLevelOrgCristinId().orElseThrow())) {
            throw new ForbiddenException();
        }
    }

    private Optional<Candidate> fetchCandidate(DbApprovalStatus dbApprovalStatus) {
        return nviService.findCandidateById(dbApprovalStatus.candidateIdentifier());
    }

    private DbApprovalStatus toApprovalStatus(NviStatusRequest input, UUID candidateIdentifier) {
        return DbApprovalStatus.builder()
                   .candidateIdentifier(candidateIdentifier)
                   .institutionId(input.institutionId())
                   .build();
    }
}
