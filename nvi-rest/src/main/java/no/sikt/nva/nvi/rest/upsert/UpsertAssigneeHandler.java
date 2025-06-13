package no.sikt.nva.nvi.rest.upsert;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.DynamoRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.service.CandidateResponseFactory;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.rest.ViewingScopeHandler;
import no.sikt.nva.nvi.rest.model.UpsertAssigneeRequest;
import no.unit.nva.clients.IdentityServiceClient;
import no.unit.nva.clients.UserDto;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class UpsertAssigneeHandler extends ApiGatewayHandler<UpsertAssigneeRequest, CandidateDto>
    implements ViewingScopeHandler {

  public static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
  private final IdentityServiceClient identityServiceClient;
  private final ViewingScopeValidator viewingScopeValidator;

  @JacocoGenerated
  public UpsertAssigneeHandler() {
    this(
        new CandidateRepository(DynamoRepository.defaultDynamoClient()),
        new PeriodRepository(DynamoRepository.defaultDynamoClient()),
        IdentityServiceClient.prepare(),
        ViewingScopeHandler.defaultViewingScopeValidator(),
        new Environment());
  }

  public UpsertAssigneeHandler(
      CandidateRepository candidateRepository,
      PeriodRepository periodRepository,
      IdentityServiceClient identityServiceClient,
      ViewingScopeValidator viewingScopeValidator,
      Environment environment) {
    super(UpsertAssigneeRequest.class, environment);
    this.candidateRepository = candidateRepository;
    this.periodRepository = periodRepository;
    this.identityServiceClient = identityServiceClient;
    this.viewingScopeValidator = viewingScopeValidator;
  }

  @Override
  protected void validateRequest(
      UpsertAssigneeRequest input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    validateCustomerAndAccessRight(input, requestInfo);
  }

  @Override
  protected CandidateDto processInput(
      UpsertAssigneeRequest input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    var candidateIdentifier = UUID.fromString(requestInfo.getPathParameter(CANDIDATE_IDENTIFIER));
    var institutionId = input.institutionId();
    var assignee = input.assignee();
    var userInstance = UserInstance.fromRequestInfo(requestInfo);
    return attempt(
            () -> Candidate.fetch(() -> candidateIdentifier, candidateRepository, periodRepository))
        .map(
            candidate ->
                validateViewingScope(
                    viewingScopeValidator, RequestUtil.getUsername(requestInfo), candidate))
        .map(
            candidate ->
                candidate.updateApprovalAssignee(
                    new UpdateAssigneeRequest(institutionId, assignee)))
        .map(candidate -> CandidateResponseFactory.create(candidate, userInstance))
        .orElseThrow(ExceptionMapper::map);
  }

  @Override
  protected Integer getSuccessStatusCode(UpsertAssigneeRequest input, CandidateDto output) {
    return HttpURLConnection.HTTP_OK;
  }

  private static void hasSameCustomer(UpsertAssigneeRequest input, RequestInfo requestInfo)
      throws UnauthorizedException {
    if (!input.institutionId().equals(requestInfo.getTopLevelOrgCristinId().orElseThrow())) {
      throw new UnauthorizedException();
    }
  }

  private void validateCustomerAndAccessRight(UpsertAssigneeRequest input, RequestInfo requestInfo)
      throws UnauthorizedException {
    RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATES);
    hasSameCustomer(input, requestInfo);
    if (nonNull(input.assignee())) {
      assigneeHasAccessRight(input.assignee());
    }
  }

  private void assigneeHasAccessRight(String assignee) throws UnauthorizedException {
    try {
      var user = identityServiceClient.getUser(assignee);
      if (isNull(user) || !isIsNviCurator(user)) {
        throw new UnauthorizedException();
      }
    } catch (NotFoundException e) {
      throw new UnauthorizedException();
    }
  }

  private static boolean isIsNviCurator(UserDto user) {
    return user.accessRights().contains(AccessRight.MANAGE_NVI_CANDIDATES.name());
  }
}
