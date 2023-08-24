package no.sikt.nva.nvi.rest.utils;

import no.sikt.nva.nvi.rest.model.NviPeriodDto;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadMethodException;
import nva.commons.apigateway.exceptions.ConflictException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.attempt.Failure;

public final class ExceptionMapper {

    private ExceptionMapper() {
    }

    public static ApiGatewayException map(Failure<NviPeriodDto> failure) {
        var exception = failure.getException();
        if (exception instanceof NotFoundException) {
            return new NotFoundException("Resource not found!");
        }
        if (exception instanceof BadMethodException) {
            return new BadMethodException("Operation is not allowed!");
        }
        if (exception instanceof ConflictException) {
            return new ConflictException("Conflict occurred!");
        } else {
            return (ApiGatewayException) exception;
        }
    }
}