package no.sikt.nva.nvi.upsert;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.fetch.FetchNviCandidateHandler.PARAM_CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.rest.utils.RequestUtil.getUsername;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.Username;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.NviApprovalStatus;
import no.sikt.nva.nvi.rest.NviStatusRequest;
import no.sikt.nva.nvi.rest.utils.RequestUtil;
import no.sikt.nva.nvi.utils.ExceptionMapper;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.JacocoGenerated;

public class UpsertNviCandidateStatusHandler extends ApiGatewayHandler<NviStatusRequest, CandidateResponse> {

    private final NviService nviService;

    @JacocoGenerated
    public UpsertNviCandidateStatusHandler() {
        this(new NviService(defaultDynamoClient()));
    }

    public UpsertNviCandidateStatusHandler(NviService service) {
        super(NviStatusRequest.class);
        this.nviService = service;
    }

    @Override
    protected CandidateResponse processInput(NviStatusRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATE);

        return attempt(() -> toStatus(input, getUsername(requestInfo)))
                   .map(approvalStatus -> nviService.upsertApproval(
                       UUID.fromString(requestInfo.getPathParameter(PARAM_CANDIDATE_IDENTIFIER)),
                       approvalStatus))
                   .map(CandidateResponse::fromCandidate)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(NviStatusRequest input, CandidateResponse output) {
        return HTTP_OK;
    }

    private ApprovalStatus toStatus(NviStatusRequest input, Username username) {
        return new ApprovalStatus(input.institutionId(), mapStatus(input.status()), username, Instant.now());
    }

    // New switch return syntax isn't fullfilling the 100% coverage because of a bug.
    // TODO remove JacocoGenerated when 0.8.11 is release with a possible fix
    // https://www.jacoco.org/jacoco/trunk/doc/changes.html
    // https://github.com/jacoco/jacoco/pull/1472
    @JacocoGenerated
    private Status mapStatus(NviApprovalStatus status) {
        return switch (status) {
            case APPROVED -> Status.APPROVED;
            case PENDING -> Status.PENDING;
            case REJECTED -> Status.REJECTED;
        };
    }
}
