package no.sikt.nva.nvi.utils;

import java.util.NoSuchElementException;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExceptionMapper {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionMapper.class);

    private ExceptionMapper() {
    }

    public static <T> ApiGatewayException map(Failure<T> failure) {
        var exception = failure.getException();
        if (exception instanceof NotFoundException || exception instanceof NoSuchElementException) {
            logger.error("NotFoundException", exception);
            return new NotFoundException("Resource not found!");
        }
        if (exception instanceof IllegalArgumentException || exception instanceof UnsupportedOperationException) {
            logger.error("IllegalArgumentException", exception);
            return new BadRequestException(exception.getMessage());
        }
        if (exception instanceof IllegalStateException) {
            logger.error("IllegalStateException", exception);
            return new BadRequestException(exception.getMessage());
        }
        logger.error("BadGatewayException", exception);
        return new BadGatewayException(exception.getMessage());
    }
}
