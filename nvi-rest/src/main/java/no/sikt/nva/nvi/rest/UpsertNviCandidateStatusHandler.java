package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.common.utils.RequestUtil.getUsername;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.time.Instant;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.Username;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import no.sikt.nva.nvi.rest.utils.ExceptionMapper;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;

public class UpsertNviCandidateStatusHandler extends ApiGatewayHandler<NviStatusRequest, NviStatusResponse> {

    private final NviService nviService;

    public UpsertNviCandidateStatusHandler() {
        this(new NviService(null));
    }

    public UpsertNviCandidateStatusHandler(NviService service) {
        super(NviStatusRequest.class);
        this.nviService = service;
    }

    @Override
    protected NviStatusResponse processInput(NviStatusRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        //TODO CHANGE TO CORRECT ACCESS RIGHT
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_PERIODS);

        return attempt(() -> toStatus(input, getUsername(requestInfo)))
                   .map(nviService::upsertApproval)
                   //                   .map(NviPeriod::copy)
                   //                   .map(builder -> builder.withCreatedBy(getUsername(requestInfo)))
                   //                   .map(Builder::build)
                   //                   .map(nviService::createPeriod)
                   //                   .map(NviPeriodDto::fromNviPeriod)
                   .map(this::toResponse)
                   .orElseThrow(failure -> ExceptionMapper.map(failure.getException()));
    }

    @Override
    protected Integer getSuccessStatusCode(NviStatusRequest input, NviStatusResponse output) {
        return null;
    }

    private NviStatusResponse toResponse(Candidate approvalStatus) {
        return new NviStatusResponse();
    }

    private ApprovalStatus toStatus(NviStatusRequest input, Username username) {
        return new ApprovalStatus(input.institutionId(), mapStatus(input.status()), username, Instant.now());
    }

    private Status mapStatus(NviApprovalStatus status) {
        return switch (status) {
            case APPROVED -> Status.APPROVED;
            case PENDING -> Status.PENDING;
            case REJECTED -> Status.REJECTED;
        };
    }
}
