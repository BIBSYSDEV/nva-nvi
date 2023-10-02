package no.sikt.nva.nvi.rest.upsert;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.utils.ExceptionMapper;
import no.sikt.nva.nvi.utils.RequestUtil;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

public class UpdateNviCandidateStatusHandler extends ApiGatewayHandler<NviStatusRequest, CandidateDto> {

    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;

    @JacocoGenerated
    public UpdateNviCandidateStatusHandler() {
        this(new CandidateRepository(defaultDynamoClient()), new PeriodRepository(defaultDynamoClient()));
    }

    public UpdateNviCandidateStatusHandler(CandidateRepository candidateRepository, PeriodRepository periodRepository) {
        super(NviStatusRequest.class);
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
    }

    @Override
    protected CandidateDto processInput(NviStatusRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        validateRequest(input, requestInfo);
        var candidateIdentifier = UUID.fromString(requestInfo.getPathParameter(CANDIDATE_IDENTIFIER));
        var username = requestInfo.getUserName();

        return attempt(() -> CandidateBO.fromRequest(() -> candidateIdentifier, candidateRepository, periodRepository))
                   .map(candidate -> candidate.updateStatus(input.toUpdateRequest(username)))
                   .orElseThrow(ExceptionMapper::map)
                   .toDto();
    }

    @Override
    protected Integer getSuccessStatusCode(NviStatusRequest input, CandidateDto output) {
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
}
