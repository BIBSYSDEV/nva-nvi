package no.sikt.nva.nvi.index.apigateway;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.common.net.MediaType;
import java.net.HttpURLConnection;
import java.util.List;
import no.sikt.nva.nvi.index.xlsx.ExcelWorkbookGenerator;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.UnauthorizedException;

public class FetchInstitutionReportHandler extends ApiGatewayHandler<Void, String> {

    public FetchInstitutionReportHandler() {
        super(Void.class);
    }

    @Override
    protected List<MediaType> listSupportedMediaTypes() {
        // The first media type in the list is the default media type if the client does not specify a media type
        // See RestRequestHandler#defaultResponseContentTypeWhenNotSpecifiedByClientRequest
        return List.of(MediaType.OOXML_SHEET, MediaType.MICROSOFT_EXCEL);
    }

    @Override
    protected void validateRequest(Void unused, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        if (!requestInfo.userIsAuthorized(AccessRight.MANAGE_NVI_CANDIDATES)) {
            throw new UnauthorizedException();
        }
    }

    @Override
    protected String processInput(Void unused, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        setIsBase64Encoded(true);
        return new ExcelWorkbookGenerator(List.of("header"),
                                          List.of(List.of("value"))).toBase64EncodedString();
    }

    @Override
    protected Integer getSuccessStatusCode(Void unused, String o) {
        return HttpURLConnection.HTTP_OK;
    }
}
