package no.sikt.nva.nvi.rest.create;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.common.utils.RequestUtil.hasAccessRight;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.exceptions.NotApplicableException;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.Username;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.rest.ViewingScopeHandler;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class CreateNoteHandler extends ApiGatewayHandler<NviNoteRequest, CandidateDto>
    implements ViewingScopeHandler {

  public static final String INVALID_REQUEST_ERROR = "Request body must contain text field.";
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
  private final ViewingScopeValidator viewingScopeValidator;
  private final OrganizationRetriever organizationRetriever;

  @JacocoGenerated
  public CreateNoteHandler() {
    this(
        new CandidateRepository(defaultDynamoClient()),
        new PeriodRepository(defaultDynamoClient()),
        ViewingScopeHandler.defaultViewingScopeValidator(),
        new OrganizationRetriever(new UriRetriever()),
        new Environment());
  }

  public CreateNoteHandler(
      CandidateRepository candidateRepository,
      PeriodRepository periodRepository,
      ViewingScopeValidator viewingScopeValidator,
      OrganizationRetriever organizationRetriever,
      Environment environment) {
    super(NviNoteRequest.class, environment);
    this.candidateRepository = candidateRepository;
    this.periodRepository = periodRepository;
    this.viewingScopeValidator = viewingScopeValidator;
    this.organizationRetriever = organizationRetriever;
  }

  @Override
  protected void validateRequest(NviNoteRequest input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATES);
    validate(input);
  }

  @Override
  protected CandidateDto processInput(
      NviNoteRequest input, RequestInfo requestInfo, Context context) throws ApiGatewayException {
    var username = RequestUtil.getUsername(requestInfo);
    var candidateIdentifier = UUID.fromString(requestInfo.getPathParameter(CANDIDATE_IDENTIFIER));
    var institutionId = requestInfo.getTopLevelOrgCristinId().orElseThrow();
    return attempt(
            () -> Candidate.fetch(() -> candidateIdentifier, candidateRepository, periodRepository))
        .map(this::checkIfApplicable)
        .map(candidate -> validateViewingScope(viewingScopeValidator, username, candidate))
        .map(candidate -> createNote(input, candidate, username, institutionId))
        .map(candidate -> toCandidateDto(requestInfo, candidate))
        .orElseThrow(ExceptionMapper::map);
  }

  private CandidateDto toCandidateDto(RequestInfo requestInfo, Candidate candidate) {
    return candidate.toDto(
        requestInfo.getTopLevelOrgCristinId().orElseThrow(), organizationRetriever);
  }

  @Override
  protected Integer getSuccessStatusCode(NviNoteRequest input, CandidateDto output) {
    return HttpURLConnection.HTTP_OK;
  }

  private Candidate createNote(
      NviNoteRequest input, Candidate candidate, Username username, URI institutionId) {
    var noteRequest = new CreateNoteRequest(input.text(), username.value(), institutionId);
    return candidate.createNote(noteRequest, candidateRepository);
  }

  private Candidate checkIfApplicable(Candidate candidate) {
    if (candidate.isApplicable()) {
      return candidate;
    }
    throw new NotApplicableException();
  }

  private void validate(NviNoteRequest input) throws BadRequestException {
    if (Objects.isNull(input.text())) {
      throw new BadRequestException(INVALID_REQUEST_ERROR);
    }
  }
}
