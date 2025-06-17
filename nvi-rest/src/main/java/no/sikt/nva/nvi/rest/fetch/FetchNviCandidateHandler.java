package no.sikt.nva.nvi.rest.fetch;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviAdmin;
import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviCurator;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.service.CandidateResponseFactory;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.rest.ViewingScopeHandler;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class FetchNviCandidateHandler extends ApiGatewayHandler<Void, CandidateDto>
    implements ViewingScopeHandler {

  public static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;

  @JacocoGenerated
  public FetchNviCandidateHandler() {
    this(
        new CandidateRepository(defaultDynamoClient()),
        new PeriodRepository(defaultDynamoClient()),
        new Environment());
  }

  public FetchNviCandidateHandler(
      CandidateRepository candidateRepository,
      PeriodRepository periodRepository,
      Environment environment) {
    super(Void.class, environment);
    this.candidateRepository = candidateRepository;
    this.periodRepository = periodRepository;
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    validateAccessRight(requestInfo);
  }

  @Override
  protected CandidateDto processInput(Void input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    var userInstance = UserInstance.fromRequestInfo(requestInfo);
    return attempt(() -> requestInfo.getPathParameter(CANDIDATE_IDENTIFIER))
        .map(UUID::fromString)
        .map(identifier -> Candidate.fetch(() -> identifier, candidateRepository, periodRepository))
        .map(this::checkIfApplicable)
        .map(candidate -> CandidateResponseFactory.create(candidate, userInstance))
        .orElseThrow(ExceptionMapper::map);
  }

  @Override
  protected Integer getSuccessStatusCode(Void input, CandidateDto output) {
    return HTTP_OK;
  }

  private Candidate checkIfApplicable(Candidate candidate) {
    if (candidate.isApplicable()) {
      return candidate;
    }
    throw new CandidateNotFoundException();
  }

  private static void validateAccessRight(RequestInfo requestInfo) throws UnauthorizedException {
    if (isNviCurator(requestInfo) || isNviAdmin(requestInfo)) {
      return;
    }
    throw new UnauthorizedException();
  }
}
