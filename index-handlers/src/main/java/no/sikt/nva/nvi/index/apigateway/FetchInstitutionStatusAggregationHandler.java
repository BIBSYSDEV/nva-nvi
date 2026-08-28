package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.common.utils.RequestUtil.isEditor;
import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviAdmin;
import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviCurator;
import static no.sikt.nva.nvi.index.query.SearchAggregation.ORGANIZATION_APPROVAL_STATUS_AGGREGATION;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import no.sikt.nva.nvi.index.model.report.InstitutionStatusAggregationReport;
import no.sikt.nva.nvi.index.model.report.InstitutionStatusAggregationReportMapper;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.search.SearchResultParameters;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;

public class FetchInstitutionStatusAggregationHandler
    extends ApiGatewayHandler<Void, InstitutionStatusAggregationReport> {

  private static final String PATH_PARAM_YEAR = "year";
  private static final String ENV_VAR_API_HOST = "API_HOST";
  private final CandidateSearchClient searchClient;
  private final String apiHost;

  @JacocoGenerated
  public FetchInstitutionStatusAggregationHandler() {
    this(CandidateSearchClient.defaultOpenSearchClient(), new Environment());
  }

  public FetchInstitutionStatusAggregationHandler(
      CandidateSearchClient searchClient, Environment environment) {
    super(Void.class, environment);
    this.searchClient = searchClient;
    this.apiHost = environment.readEnv(ENV_VAR_API_HOST);
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    validateAccessRight(requestInfo);
    RequestedInstitution.validateQueryParameterAccess(requestInfo);
  }

  @Override
  protected InstitutionStatusAggregationReport processInput(
      Void input, RequestInfo requestInfo, Context context) {
    var topLevelOrg = RequestedInstitution.resolve(requestInfo, apiHost);
    var year = requestInfo.getPathParameter(PATH_PARAM_YEAR);
    var aggregate = getAggregate(year, topLevelOrg);
    return InstitutionStatusAggregationReportMapper.fromAggregation(aggregate, year, topLevelOrg);
  }

  @Override
  protected Integer getSuccessStatusCode(Void input, InstitutionStatusAggregationReport output) {
    return HttpURLConnection.HTTP_OK;
  }

  private static void validateAccessRight(RequestInfo requestInfo) throws UnauthorizedException {
    if (!hasAccess(requestInfo)) {
      throw new UnauthorizedException();
    }
  }

  private static boolean hasAccess(RequestInfo requestInfo) {
    return isNviCurator(requestInfo)
        || isEditor(requestInfo)
        || isNviAdmin(requestInfo)
        || requestInfo.clientIsInternalBackend();
  }

  private Aggregate getAggregate(String year, URI requestedInstitution) {
    var searchParameters =
        CandidateSearchParameters.builder()
            .withAggregation(ORGANIZATION_APPROVAL_STATUS_AGGREGATION)
            .withYear(year)
            .withTopLevelCristinOrg(requestedInstitution)
            .withSearchResultParameters(aggregationOnlyResultParameters())
            .build();
    var searchResponse = attempt(() -> searchClient.search(searchParameters)).orElseThrow();
    return searchResponse
        .aggregations()
        .get(ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName());
  }

  private static SearchResultParameters aggregationOnlyResultParameters() {
    return SearchResultParameters.builder().withSize(0).build();
  }
}
