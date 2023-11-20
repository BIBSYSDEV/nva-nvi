package no.sikt.nva.nvi.rest.fetch;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.utils.RequestUtil.hasAccessRight;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATE;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

public class FetchNviCandidateHandler extends ApiGatewayHandler<Void, CandidateDto> {

    public static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;

    @JacocoGenerated
    public FetchNviCandidateHandler() {
        this(new CandidateRepository(defaultDynamoClient()), new PeriodRepository(defaultDynamoClient()));
    }

    public FetchNviCandidateHandler(CandidateRepository candidateRepository, PeriodRepository periodRepository) {
        super(Void.class);
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
    }

    @Override
    protected CandidateDto processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        validateAccessRight(requestInfo);
        var topLevelCristinOrg = requestInfo.getTopLevelOrgCristinId().orElseThrow();
        return attempt(() -> requestInfo.getPathParameter(CANDIDATE_IDENTIFIER)).map(UUID::fromString)
                   .map(identifier -> CandidateBO.fromRequest(() -> identifier, candidateRepository, periodRepository))
                   .map(this::checkIfApplicable)
                   .map(candidate -> validateTopLevelOrg(candidate, topLevelCristinOrg))
                   .map(CandidateBO::toDto)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, CandidateDto output) {
        return HTTP_OK;
    }

    private static void validateAccessRight(RequestInfo requestInfo) throws UnauthorizedException {
        hasAccessRight(requestInfo, MANAGE_NVI_CANDIDATE);
    }

    private static CandidateBO validateTopLevelOrg(CandidateBO candidate, URI topLevelOrgCristinId)
        throws UnauthorizedException {
        if (doesNotContainApprovalForInstitution(candidate, topLevelOrgCristinId)) {
            throw new UnauthorizedException();
        }
        return candidate;
    }

    private static boolean doesNotContainApprovalForInstitution(CandidateBO candidate, URI topLevelOrgCristinId) {
        return candidate.getApprovals().keySet().stream().noneMatch(uri -> uri.equals(topLevelOrgCristinId));
    }

    private CandidateBO checkIfApplicable(CandidateBO candidate) {
        if (candidate.isApplicable()) {
            return candidate;
        }
        throw new CandidateNotFoundException();
    }
}
