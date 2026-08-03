package no.sikt.nva.nvi.rest.fetch;

import static java.net.HttpURLConnection.HTTP_OK;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

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
    return attempt(() -> getCandidateStatus(requestInfo)).orElseThrow(ExceptionMapper::map);
  }

  @Override
  protected Integer getSuccessStatusCode(Void input, ReportStatusDto output) {
    return HTTP_OK;
  }

  private ReportStatusDto getCandidateStatus(RequestInfo requestInfo) {
    var publicationId = getPublicationId(requestInfo);
    var candidate = candidateService.findCandidateByPublicationId(publicationId);
    return candidate
        .map(ReportStatusDto::fromCandidate)
        .orElseGet(() -> ReportStatusDto.forNonCandidate(publicationId));
  }

  private URI getPublicationId(RequestInfo requestInfo) {
    return RequestUtil.getPublicationId(requestInfo, PATH_PARAM_PUBLICATION_ID, environment);
  }
}
