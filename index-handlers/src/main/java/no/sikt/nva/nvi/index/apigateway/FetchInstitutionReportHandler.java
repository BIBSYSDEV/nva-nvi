package no.sikt.nva.nvi.index.apigateway;

import static java.lang.Integer.parseInt;
import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviAdmin;
import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviCurator;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Year;
import java.util.List;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.utils.InstitutionReportGenerator;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.MediaType;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;

public class FetchInstitutionReportHandler extends ApiGatewayHandler<Void, String> {

  private static final Logger logger =
      org.slf4j.LoggerFactory.getLogger(FetchInstitutionReportHandler.class);
  private static final String PATH_PARAMETER_YEAR = "year";
  private static final String QUERY_PARAM_INSTITUTION = "institution";
  private static final String QUERY_PARAM_INSTITUTION_ALL = "all";
  private static final String QUERY_PARAM_SECTOR = "sector";
  private static final String ENV_VAR_INSTITUTION_REPORT_SEARCH_PAGE_SIZE =
      "INSTITUTION_REPORT_SEARCH_PAGE_SIZE";
  private final SearchClient<NviCandidateIndexDocument> searchClient;

  @JacocoGenerated
  public FetchInstitutionReportHandler() {
    this(OpenSearchClient.defaultOpenSearchClient(), new Environment());
  }

  public FetchInstitutionReportHandler(
      SearchClient<NviCandidateIndexDocument> searchClient, Environment environment) {
    super(Void.class, environment);
    this.searchClient = searchClient;
  }

  @Override
  protected List<MediaType> listSupportedMediaTypes() {
    // The first media type in the list is the default media type if the client does not specify a
    // media type
    // See RestRequestHandler#defaultResponseContentTypeWhenNotSpecifiedByClientRequest
    return List.of(MediaType.OOXML_SHEET, MediaType.MICROSOFT_EXCEL);
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    if (!hasReportAccess(requestInfo)) {
      throw new UnauthorizedException();
    }
    if (isInvalidPathParameterYear(requestInfo)) {
      throw new BadRequestException("Invalid path parameter 'year'");
    }
    validateInstitutionParam(requestInfo);
    validateSectorParam(requestInfo);
  }

  @Override
  protected String processInput(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    setIsBase64Encoded(true);
    var year = requestInfo.getPathParameter(PATH_PARAMETER_YEAR);
    var institutionId = resolveInstitutionId(requestInfo);
    var sector = requestInfo.getQueryParameterOpt(QUERY_PARAM_SECTOR).orElse(null);
    logger.info("Generating report for institution {} for year {}", institutionId, year);
    var pageSize = parseInt(new Environment().readEnv(ENV_VAR_INSTITUTION_REPORT_SEARCH_PAGE_SIZE));
    var report =
        new InstitutionReportGenerator(searchClient, pageSize, year, institutionId, sector)
            .generateReport();
    logger.info("Report generated successfully. Returning report as base64 encoded string");
    return report.toBase64EncodedString();
  }

  @Override
  protected Integer getSuccessStatusCode(Void unused, String o) {
    return HttpURLConnection.HTTP_OK;
  }

  private static boolean hasReportAccess(RequestInfo requestInfo) {
    return isNviAdmin(requestInfo)
        || isNviCurator(requestInfo)
        || requestInfo.clientIsInternalBackend();
  }

  private static boolean canQueryAnyInstitution(RequestInfo requestInfo) {
    return isNviAdmin(requestInfo) || requestInfo.clientIsInternalBackend();
  }

  private static URI resolveInstitutionId(RequestInfo requestInfo) {
    var institutionParam = requestInfo.getQueryParameterOpt(QUERY_PARAM_INSTITUTION);
    if (institutionParam.isPresent()) {
      return QUERY_PARAM_INSTITUTION_ALL.equalsIgnoreCase(institutionParam.get())
          ? null
          : URI.create(institutionParam.get());
    }
    return requestInfo.getTopLevelOrgCristinId().orElse(null);
  }

  private static void validateInstitutionParam(RequestInfo requestInfo)
      throws UnauthorizedException {
    if (canQueryAnyInstitution(requestInfo)) {
      return;
    }
    var institutionParam = requestInfo.getQueryParameterOpt(QUERY_PARAM_INSTITUTION);
    if (institutionParam.isEmpty()) {
      return;
    }
    var curatorOrg =
        requestInfo.getTopLevelOrgCristinId().orElseThrow(UnauthorizedException::new).toString();
    if (!curatorOrg.equals(institutionParam.get())) {
      throw new UnauthorizedException();
    }
  }

  private static void validateSectorParam(RequestInfo requestInfo) throws BadRequestException {
    var sectorParam = requestInfo.getQueryParameterOpt(QUERY_PARAM_SECTOR);
    if (sectorParam.isPresent() && Sector.fromString(sectorParam.get()).isEmpty()) {
      throw new BadRequestException("Invalid query parameter 'sector'");
    }
  }

  private static boolean isInvalidPathParameterYear(RequestInfo requestInfo) {
    return attempt(() -> Year.of(parseInt(requestInfo.getPathParameter(PATH_PARAMETER_YEAR))))
        .isFailure();
  }
}
