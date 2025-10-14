package no.sikt.nva.nvi.rest.fetch;

import static java.net.HttpURLConnection.HTTP_OK;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.rest.fetch.ReportStatusDto.StatusDto;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;

public class FetchReportStatusByPublicationIdHandler
    extends ApiGatewayHandler<Void, ReportStatusDto> {

  private static final String PATH_PARAM_PUBLICATION_ID = "publicationId";
  private final CandidateService candidateService;

  @JacocoGenerated
  public FetchReportStatusByPublicationIdHandler() {
    this(CandidateService.defaultCandidateService(), new Environment());
  }

  public FetchReportStatusByPublicationIdHandler(
      CandidateService candidateService, Environment environment) {
    super(Void.class, environment);
    this.candidateService = candidateService;
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {}

  @Override
  protected ReportStatusDto processInput(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    var publicationId = getPublicationId(requestInfo);
    return attempt(() -> candidateService.getByPublicationId(publicationId))
        .map(ReportStatusDto::fromCandidate)
        .orElse(failure -> handleNotFoundOrFailure(failure, publicationId));
  }

  @Override
  protected Integer getSuccessStatusCode(Void input, ReportStatusDto output) {
    return HTTP_OK;
  }

  private static ReportStatusDto handleNotFoundOrFailure(
      Failure<ReportStatusDto> failure, URI publicationId) throws ApiGatewayException {
    if (failure.getException() instanceof CandidateNotFoundException) {
      return ReportStatusDto.builder()
          .withPublicationId(publicationId)
          .withStatus(StatusDto.NOT_CANDIDATE)
          .build();
    } else {
      throw ExceptionMapper.map(failure);
    }
  }

  private URI getPublicationId(RequestInfo requestInfo) {
    return URI.create(
        URLDecoder.decode(
            requestInfo.getPathParameters().get(PATH_PARAM_PUBLICATION_ID),
            StandardCharsets.UTF_8));
  }
}
