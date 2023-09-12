package no.sikt.nva.nvi.utils;

import java.util.NoSuchElementException;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.ConflictException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.attempt.Failure;

public final class ExceptionMapper {

    private ExceptionMapper() {
    }

    public static <T> ApiGatewayException map(Failure<T> failure) {
        var exception = failure.getException();
        if (exception instanceof NotFoundException
            || exception instanceof NoSuchElementException) {
            return new NotFoundException("Resource not found!");
        }
        if (exception instanceof IllegalArgumentException) {
            return new BadRequestException(exception.getMessage());
        }
        if (exception instanceof IllegalStateException) {
            return new ConflictException("Conflict occurred!");
        }
        if (exception instanceof ForbiddenException) {
            return new ForbiddenException();
        }
        if (exception instanceof UnauthorizedException) {
            return (UnauthorizedException) exception;
        }
        return new BadGatewayException(exception.getMessage());
    }
}
