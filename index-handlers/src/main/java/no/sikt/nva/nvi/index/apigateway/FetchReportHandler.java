package no.sikt.nva.nvi.index.apigateway;

import static java.nio.charset.StandardCharsets.UTF_8;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

public class FetchReportHandler extends ApiGatewayHandler<Void, String> {

    private static final String INSTITUTION_ID = "institutionId";
    private final OpenSearchClient openSearchClient;

    @JacocoGenerated
    public FetchReportHandler() {
        this(OpenSearchClient.defaultOpenSearchClient());
    }

    public FetchReportHandler(OpenSearchClient openSearchClient) {
        super(Void.class);
        this.openSearchClient = openSearchClient;
    }

    @Override
    protected String processInput(Void input, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        var requestedInstitution = decodeInstitutionIdentifierPath(requestInfo);
        validateRequest(requestedInstitution, requestInfo);
        return null;
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
        RequestUtil.hasAccessRight(requestInfo, MANAGE_NVI_CANDIDATES);
        hasSameCustomer(requestInfo, requestedInstitution);
    }

    private static void hasSameCustomer(RequestInfo requestInfo, URI requestedInstitution) throws ForbiddenException {
        if (!requestedInstitution.equals(requestInfo.getTopLevelOrgCristinId().orElseThrow())) {
            throw new ForbiddenException();
        }
    }
}
