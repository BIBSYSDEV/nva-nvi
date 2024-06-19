package no.sikt.nva.nvi.index.apigateway;

import static java.nio.charset.StandardCharsets.UTF_8;
import static no.sikt.nva.nvi.common.utils.RequestUtil.hasAccessRight;
import static no.sikt.nva.nvi.index.query.SearchAggregation.ORGANIZATION_APPROVAL_STATUS_AGGREGATION;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Map;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.utils.AggregationFormatter;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

public class FetchInstitutionStatusAggregationHandler extends ApiGatewayHandler<Void, String> {

    public static final String PATH_PARAM_YEAR = "year";
    private static final String INSTITUTION_ID = "institutionId";
    private final OpenSearchClient openSearchClient;

    @JacocoGenerated
    public FetchInstitutionStatusAggregationHandler() {
        this(OpenSearchClient.defaultOpenSearchClient());
    }

    public FetchInstitutionStatusAggregationHandler(OpenSearchClient openSearchClient) {
        super(Void.class);
        this.openSearchClient = openSearchClient;
    }

    @Override
    protected String processInput(Void input, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        var requestedInstitution = decodeInstitutionIdentifierPath(requestInfo);
        var year = requestInfo.getPathParameter(PATH_PARAM_YEAR);
        validateRequest(requestedInstitution, requestInfo);
        var aggregation = ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName();
        var searchParameters = CandidateSearchParameters.builder()
                                   .withAggregationType(aggregation)
                                   .withYear(year)
                                   .withTopLevelCristinOrg(requestedInstitution)
                                   .build();
        var searchResponse = attempt(() -> openSearchClient.search(searchParameters)).orElseThrow();
        var aggregate = searchResponse.aggregations().get(aggregation);
        return AggregationFormatter.format(Map.of(aggregation, aggregate))
                   .at(("/organizationApprovalStatuses"))
                   .get(requestedInstitution.toString())
                   .at("/" + "organizations")
                   .toPrettyString();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, String output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static URI decodeInstitutionIdentifierPath(RequestInfo requestInfo) {
        return URI.create(URLDecoder.decode(requestInfo.getPathParameter(INSTITUTION_ID), UTF_8));
    }

    private static void validateRequest(URI requestedInstitution, RequestInfo requestInfo)
        throws UnauthorizedException, ForbiddenException {
        hasAccessRight(requestInfo, MANAGE_NVI_CANDIDATES);
        hasSameCustomer(requestInfo, requestedInstitution);
    }

    private static void hasSameCustomer(RequestInfo requestInfo, URI requestedInstitution) throws ForbiddenException {
        if (!requestedInstitution.equals(requestInfo.getTopLevelOrgCristinId().orElseThrow())) {
            throw new ForbiddenException();
        }
    }
}
