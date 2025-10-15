package no.sikt.nva.nvi.rest.remove;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.service.CandidateResponseFactory;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.rest.ViewingScopeHandler;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class RemoveNoteHandler extends ApiGatewayHandler<Void, CandidateDto>
    implements ViewingScopeHandler {

  public static final String PARAM_NOTE_IDENTIFIER = "noteIdentifier";
  private final CandidateService candidateService;
  private final ViewingScopeValidator viewingScopeValidator;

  @JacocoGenerated
  public RemoveNoteHandler() {
    this(
        CandidateService.defaultCandidateService(),
        ViewingScopeHandler.defaultViewingScopeValidator(),
        new Environment());
  }

  public RemoveNoteHandler(
      CandidateService candidateService,
      ViewingScopeValidator viewingScopeValidator,
      Environment environment) {
    super(Void.class, environment);
    this.candidateService = candidateService;
    this.viewingScopeValidator = viewingScopeValidator;
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATES);
  }

  @Override
  protected CandidateDto processInput(Void input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    var candidateIdentifier = UUID.fromString(requestInfo.getPathParameter(CANDIDATE_IDENTIFIER));
    var noteIdentifier = UUID.fromString(requestInfo.getPathParameter(PARAM_NOTE_IDENTIFIER));
    var user = UserInstance.fromRequestInfo(requestInfo);
    var deleteNoteRequest = new DeleteNoteRequest(noteIdentifier, user.userName().value());

    return attempt(() -> candidateService.getByIdentifier(candidateIdentifier))
        .map(candidate -> validateViewingScope(viewingScopeValidator, user.userName(), candidate))
        .map(candidate -> deleteNote(candidate, deleteNoteRequest))
        .map(candidate -> CandidateResponseFactory.create(candidate, user))
        .orElseThrow(ExceptionMapper::map);
  }

  private Candidate deleteNote(Candidate candidate, DeleteNoteRequest request) {
    candidateService.deleteNote(candidate, request);
    return candidateService.getByIdentifier(candidate.identifier());
  }

  @Override
  protected Integer getSuccessStatusCode(Void input, CandidateDto output) {
    return HttpURLConnection.HTTP_OK;
  }
}
