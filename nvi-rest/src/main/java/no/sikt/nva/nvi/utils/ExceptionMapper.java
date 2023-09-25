package no.sikt.nva.nvi.utils;

import java.util.NoSuchElementException;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.attempt.Failure;

public final class ExceptionMapper {

    private ExceptionMapper() {
    }

    public static <T> ApiGatewayException map(Failure<T> failure) {
        var exception = failure.getException();
        if (exception instanceof NotFoundException || exception instanceof NoSuchElementException) {
            return new NotFoundException("Resource not found!");
        }
        if (exception instanceof IllegalArgumentException) {
            return new BadRequestException(exception.getMessage());
        }
        if (exception instanceof IllegalStateException) {
            return new BadRequestException(exception.getMessage());
        }
        return new BadGatewayException(exception.getMessage());
    }
}
