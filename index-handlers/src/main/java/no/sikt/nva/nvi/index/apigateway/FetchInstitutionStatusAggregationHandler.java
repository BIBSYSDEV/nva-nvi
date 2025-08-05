package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.common.utils.RequestUtil.hasAccessRight;
import static no.sikt.nva.nvi.index.query.Aggregations.APPROVAL_ORGANIZATIONS_AGGREGATION;
import static no.sikt.nva.nvi.index.query.SearchAggregation.ORGANIZATION_APPROVAL_STATUS_AGGREGATION;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.utils.AggregationFormatter;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;

public class FetchInstitutionStatusAggregationHandler extends ApiGatewayHandler<Void, String> {

  public static final String PATH_PARAM_YEAR = "year";
  public static final String DELIMITER = "/";
  public static final String AGGREGATION =
      ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName();
  private final OpenSearchClient openSearchClient;

  @JacocoGenerated
  public FetchInstitutionStatusAggregationHandler() {
    this(OpenSearchClient.defaultOpenSearchClient(), new Environment());
  }

  public FetchInstitutionStatusAggregationHandler(
      OpenSearchClient openSearchClient, Environment environment) {
    super(Void.class, environment);
    this.openSearchClient = openSearchClient;
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    validateAccessRight(requestInfo);
  }

  @Override
  protected String processInput(Void input, RequestInfo requestInfo, Context context) {
    var topLevelOrg = requestInfo.getTopLevelOrgCristinId().orElseThrow();
    var year = requestInfo.getPathParameter(PATH_PARAM_YEAR);
    var result = getAggregate(year, topLevelOrg);
    return format(result, topLevelOrg);
  }

  @Override
  protected Integer getSuccessStatusCode(Void input, String output) {
    return HttpURLConnection.HTTP_OK;
  }

  private static String format(Aggregate aggregate, URI topLevelOrg) {
    return AggregationFormatter.format(Map.of(AGGREGATION, aggregate))
        .at(DELIMITER + AGGREGATION)
        .get(topLevelOrg.toString())
        .at(DELIMITER + APPROVAL_ORGANIZATIONS_AGGREGATION)
        .toPrettyString();
  }

  private static void validateAccessRight(RequestInfo requestInfo) throws UnauthorizedException {
    hasAccessRight(requestInfo, MANAGE_NVI_CANDIDATES);
  }

  private Aggregate getAggregate(String year, URI requestedInstitution) {
    var searchParameters =
        CandidateSearchParameters.builder()
            .withAggregationType(AGGREGATION)
            .withYear(year)
            .withTopLevelCristinOrg(requestedInstitution)
            .build();
    var searchResponse = attempt(() -> openSearchClient.search(searchParameters)).orElseThrow();
    return searchResponse.aggregations().get(AGGREGATION);
  }
}
