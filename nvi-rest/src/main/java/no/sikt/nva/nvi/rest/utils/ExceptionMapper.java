package no.sikt.nva.nvi.rest.utils;

import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadMethodException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.ConflictException;
import nva.commons.apigateway.exceptions.NotFoundException;

public final class ExceptionMapper {

    private ExceptionMapper() {
    }

    public static ApiGatewayException map(Exception exception) {
        if (exception instanceof NotFoundException) {
            return new NotFoundException("Resource not found!");
        }
        if (exception instanceof BadMethodException) {
            return new BadMethodException("Operation is not allowed!");
        }
        if (exception instanceof IllegalArgumentException) {
            return new BadRequestException("");
        }
        if (exception instanceof ConflictException) {
            return new ConflictException("Conflict occurred!");
        } else {
            return (ApiGatewayException) exception;
        }
    }
}
