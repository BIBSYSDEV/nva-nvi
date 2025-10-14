package no.sikt.nva.nvi.rest.upsert;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.utils.RequestUtil.hasAccessRight;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.service.CandidateResponseFactory;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.rest.ViewingScopeHandler;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class UpdateNviCandidateStatusHandler
    extends ApiGatewayHandler<NviStatusRequest, CandidateDto> implements ViewingScopeHandler {

  private final CandidateService candidateService;
  private final ViewingScopeValidator viewingScopeValidator;

  @JacocoGenerated
  public UpdateNviCandidateStatusHandler() {
    this(
        CandidateService.defaultCandidateService(),
        ViewingScopeHandler.defaultViewingScopeValidator(),
        new Environment());
  }

  public UpdateNviCandidateStatusHandler(
      CandidateService candidateService,
      ViewingScopeValidator viewingScopeValidator,
      Environment environment) {
    super(NviStatusRequest.class, environment);
    this.candidateService = candidateService;
    this.viewingScopeValidator = viewingScopeValidator;
  }

  @Override
  protected void validateRequest(NviStatusRequest input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    input.validate();
    validateCustomerAndAccessRight(input, requestInfo);
  }

  @Override
  protected CandidateDto processInput(
      NviStatusRequest input, RequestInfo requestInfo, Context context) throws ApiGatewayException {
    var candidateIdentifier = UUID.fromString(requestInfo.getPathParameter(CANDIDATE_IDENTIFIER));
    var username = RequestUtil.getUsername(requestInfo);
    var updateRequest = input.toUpdateRequest(username.value());
    var userInstance = UserInstance.fromRequestInfo(requestInfo);

    return attempt(() -> fetchCandidate(candidateIdentifier))
        .map(candidate -> validateViewingScope(viewingScopeValidator, username, candidate))
        .map(candidate -> updateAndRefetch(candidate, updateRequest, userInstance))
        .map(candidate -> CandidateResponseFactory.create(candidate, userInstance))
        .orElseThrow(ExceptionMapper::map);
  }

  @Override
  protected Integer getSuccessStatusCode(NviStatusRequest input, CandidateDto output) {
    return HTTP_OK;
  }

  private Candidate fetchCandidate(UUID candidateIdentifier) {
    return candidateService.getByIdentifier(candidateIdentifier);
  }

  private Candidate updateAndRefetch(
      Candidate candidate, UpdateStatusRequest updateRequest, UserInstance userInstance) {
    candidate.updateApprovalStatus(updateRequest, userInstance);
    return fetchCandidate(candidate.getIdentifier());
  }

  private static void validateCustomerAndAccessRight(
      NviStatusRequest input, RequestInfo requestInfo)
      throws UnauthorizedException, ForbiddenException {
    hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATES);
    hasSameCustomer(requestInfo, input);
  }

  private static void hasSameCustomer(RequestInfo requestInfo, NviStatusRequest input)
      throws ForbiddenException {
    if (!input.institutionId().equals(requestInfo.getTopLevelOrgCristinId().orElseThrow())) {
      throw new ForbiddenException();
    }
  }
}
