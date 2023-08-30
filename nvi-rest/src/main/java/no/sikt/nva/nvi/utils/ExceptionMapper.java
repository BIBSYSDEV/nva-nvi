package no.sikt.nva.nvi.utils;

import java.util.NoSuchElementException;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadMethodException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.ConflictException;
import nva.commons.apigateway.exceptions.NotFoundException;
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
