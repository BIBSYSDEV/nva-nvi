package no.sikt.nva.nvi.common.exceptions;

import java.net.HttpURLConnection;
import nva.commons.apigateway.exceptions.ApiGatewayException;

public class MethodNotAllowedException extends ApiGatewayException {

    public MethodNotAllowedException(String message) {
        super(message);
    }

    @Override
    protected Integer statusCode() {
        return HttpURLConnection.HTTP_BAD_METHOD;
    }
}
