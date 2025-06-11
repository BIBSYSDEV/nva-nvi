package no.sikt.nva.nvi.rest.fetch;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviAdmin;
import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviCurator;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.common.validator.CandidateUpdateValidator;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.rest.ViewingScopeHandler;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class FetchNviCandidateByPublicationIdHandler extends ApiGatewayHandler<Void, CandidateDto>
    implements ViewingScopeHandler {

  public static final String CANDIDATE_PUBLICATION_ID = "candidatePublicationId";
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
  private final OrganizationRetriever organizationRetriever;
  private final ViewingScopeValidator viewingScopeValidator;

  @JacocoGenerated
  public FetchNviCandidateByPublicationIdHandler() {
    this(
        new CandidateRepository(defaultDynamoClient()),
        new PeriodRepository(defaultDynamoClient()),
        new OrganizationRetriever(new UriRetriever()),
        ViewingScopeHandler.defaultViewingScopeValidator(),
        new Environment());
  }

  public FetchNviCandidateByPublicationIdHandler(
      CandidateRepository candidateRepository,
      PeriodRepository periodRepository,
      OrganizationRetriever organizationRetriever,
      ViewingScopeValidator viewingScopeValidator,
      Environment environment) {
    super(Void.class, environment);
    this.candidateRepository = candidateRepository;
    this.periodRepository = periodRepository;
    this.organizationRetriever = organizationRetriever;
    this.viewingScopeValidator = viewingScopeValidator;
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    validateAccessRight(requestInfo);
  }

  @Override
  protected CandidateDto processInput(Void input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    return attempt(() -> getPublicationId(requestInfo))
        .map(
            identifier ->
                Candidate.fetchByPublicationId(
                    () -> identifier, candidateRepository, periodRepository))
        .map(this::checkIfApplicable)
        .map(candidate -> toCandidateDto(requestInfo, candidate))
        .orElseThrow(ExceptionMapper::map);
  }

  private CandidateDto toCandidateDto(RequestInfo requestInfo, Candidate candidate)
      throws UnauthorizedException {
    var topLevelOrganization = requestInfo.getTopLevelOrgCristinId().orElseThrow();
    var updateValidator =
        new CandidateUpdateValidator(candidate, organizationRetriever, topLevelOrganization);
    return updateValidator.getCandidateDto(requestInfo, viewingScopeValidator);
  }

  @Override
  protected Integer getSuccessStatusCode(Void input, CandidateDto output) {
    return HTTP_OK;
  }

  private URI getPublicationId(RequestInfo requestInfo) {
    return URI.create(
        URLDecoder.decode(
            requestInfo.getPathParameters().get(CANDIDATE_PUBLICATION_ID), StandardCharsets.UTF_8));
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
