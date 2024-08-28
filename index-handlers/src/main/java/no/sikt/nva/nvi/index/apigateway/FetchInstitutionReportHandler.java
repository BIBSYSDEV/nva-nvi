package no.sikt.nva.nvi.index.apigateway;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.common.net.MediaType;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.index.xlsx.Excel;
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
        return List.of(MediaType.MICROSOFT_EXCEL, MediaType.OOXML_SHEET);
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
        var emptyExcelFile = Excel.fromJava(new ArrayList<>(), new ArrayList<>());
        return emptyExcelFile.toBase64EncodedString();
    }

    @Override
    protected Integer getSuccessStatusCode(Void unused, String o) {
        return HttpURLConnection.HTTP_OK;
    }
}
