package no.sikt.nva.nvi.rest.upsert;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.PARAM_CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.rest.utils.RequestUtil.getUsername;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbStatus;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.utils.RequestUtil;
import no.sikt.nva.nvi.utils.ExceptionMapper;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
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
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATE);
        verifyRequesteeIsCorrectCustomer(input.institutionId(), requestInfo.getTopLevelOrgCristinId().orElseThrow());
        var candidateIdentifier = UUID.fromString(requestInfo.getPathParameter(PARAM_CANDIDATE_IDENTIFIER));
        return attempt(() -> toStatus(input, getUsername(requestInfo)))
                   .map(approvalStatus -> nviService.updateApprovalStatus(candidateIdentifier, approvalStatus))
                   .map(CandidateResponse::fromCandidate)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(NviStatusRequest input, CandidateResponse output) {
        return HTTP_OK;
    }

    private void verifyRequesteeIsCorrectCustomer(URI institutionId, URI currentCustomerId) throws ForbiddenException {
        if (!institutionId.toString().equals(currentCustomerId.toString())) {
            throw new ForbiddenException();
        }
    }

    private DbApprovalStatus toStatus(NviStatusRequest input, DbUsername username) {
        return DbApprovalStatus.builder()
                   .institutionId(input.institutionId())
                   .status(mapStatus(input.status()))
                   .finalizedBy(username)
                   .assignee(username)
                   .finalizedDate(Instant.now())
                   .build();
    }

    // New switch return syntax isn't fullfilling the 100% coverage because of a bug.
    // TODO remove JacocoGenerated when 0.8.11 is release with a possible fix
    // https://www.jacoco.org/jacoco/trunk/doc/changes.html
    // https://github.com/jacoco/jacoco/pull/1472
    @JacocoGenerated
    private DbStatus mapStatus(NviApprovalStatus status) {
        return switch (status) {
            case APPROVED -> DbStatus.APPROVED;
            case PENDING -> DbStatus.PENDING;
            case REJECTED -> DbStatus.REJECTED;
        };
    }
}
