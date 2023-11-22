package no.sikt.nva.nvi.events.batch;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import no.sikt.nva.nvi.events.model.ReEvaluateRequest;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReEvaluateNviCandidatesHandlerTest {

    private final Context context = mock(Context.class);
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
    }

    @Test
    void shouldThrowExceptionWhenRequestDoesNotContainYear() {
        assertThrows(IllegalArgumentException.class, () -> {
            var handler = new ReEvaluateNviCandidatesHandler();
            handler.handleRequest(eventStream(emptyRequest()), outputStream, context);
        });
    }

    private static ReEvaluateRequest emptyRequest() {
        return ReEvaluateRequest.builder().build();
    }

    private InputStream eventStream(ReEvaluateRequest input) {
        return IoUtils.stringToStream(input.toJsonString());
    }
}