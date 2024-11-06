package no.sikt.nva.nvi.common.utils;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.net.HttpURLConnection;
import java.util.NoSuchElementException;
import no.sikt.nva.nvi.common.exceptions.MethodNotAllowedException;
import no.sikt.nva.nvi.common.exceptions.NotApplicableException;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.UnauthorizedOperationException;
import nva.commons.apigateway.exceptions.BadGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.ConflictException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.attempt.Failure;
import org.junit.jupiter.api.Test;

class ExceptionMapperTest {

    @Test
    void shouldReturnNotFoundExceptionWhenMappingNotFoundException() {
        var failure = new Failure<>(new CandidateNotFoundException());
        var exception = ExceptionMapper.map(failure);
        assertEquals(NotFoundException.class, exception.getClass());
    }

    @Test
    void shouldReturnNotFoundExceptionWhenMappingNoSuchElementException() {
        var failure = new Failure<>(new NoSuchElementException());
        var exception = ExceptionMapper.map(failure);
        assertEquals(NotFoundException.class, exception.getClass());
    }

    @Test
    void shouldReturnNotFoundExceptionWhenMappingCandidateNotFoundException() {
        var failure = new Failure<>(new CandidateNotFoundException());
        var exception = ExceptionMapper.map(failure);
        assertEquals(NotFoundException.class, exception.getClass());
    }

    @Test
    void shouldReturnBadRequestExceptionWhenMappingIllegalArgumentException() {
        var failure = new Failure<>(new IllegalArgumentException());
        var exception = ExceptionMapper.map(failure);
        assertEquals(BadRequestException.class, exception.getClass());
    }

    @Test
    void shouldReturnBadRequestExceptionWhenMappingUnsupportedOperationException() {
        var failure = new Failure<>(new UnsupportedOperationException());
        var exception = ExceptionMapper.map(failure);
        assertEquals(BadRequestException.class, exception.getClass());
    }

    @Test
    void shouldReturnConflictExceptionWhenMappingIllegalStateException() {
        var failure = new Failure<>(new IllegalStateException());
        var exception = ExceptionMapper.map(failure);
        assertEquals(ConflictException.class, exception.getClass());
    }

    @Test
    void shouldReturnUnauthorizedExceptionWhenMappingUnauthorizedOperationException() {
        var failure = new Failure<>(new UnauthorizedOperationException(randomString()));
        var exception = ExceptionMapper.map(failure);
        assertEquals(UnauthorizedException.class, exception.getClass());
    }

    @Test
    void shouldReturnUnauthorizedExceptionWhenMappingUnauthorizedException() {
        var failure = new Failure<>(new UnauthorizedOperationException(randomString()));
        var exception = ExceptionMapper.map(failure);
        assertEquals(UnauthorizedException.class, exception.getClass());
    }

    @Test
    void shouldReturnMethodNotAllowedExceptionWhenMappingNotApplicableException() {
        var failure = new Failure<>(new NotApplicableException());
        var exception = ExceptionMapper.map(failure);
        assertEquals(MethodNotAllowedException.class, exception.getClass());
        assertEquals(HttpURLConnection.HTTP_BAD_METHOD, exception.getStatusCode());
    }

    @Test
    void shouldReturnBadGatewayExceptionWhenMappingOtherExceptions() {
        var failure = new Failure<>(new Exception());
        var exception = ExceptionMapper.map(failure);
        assertEquals(BadGatewayException.class, exception.getClass());
    }
}