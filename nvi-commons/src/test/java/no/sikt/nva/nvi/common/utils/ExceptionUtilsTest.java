package no.sikt.nva.nvi.common.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ExceptionUtilsTest {

    public static final String SOME_EXCEPTION_MESSAGE = "Test exception";

    @Test
    void shouldReturnStackTraceAsString() {
        var exception = new Exception(SOME_EXCEPTION_MESSAGE);
        var stackTrace = ExceptionUtils.getStackTrace(exception);
        assertTrue(stackTrace.contains(SOME_EXCEPTION_MESSAGE));
    }
}