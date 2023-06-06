package no.sikt.nva.nvi.create;

import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CreateNviCandidateHandlerTest {

    private ByteArrayOutputStream outputStream;
    private Context context;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        context = mock(Context.class);
    }

    @Test
    void shouldNotCreateCandidateWhenMissingResourceUri() {
        URI e11 = randomUri();
        var req = createRequestWithoutResourceUri(e11);
        assertThat(req.getResourceUri(),equalTo(e11));
        assertThat(req.getAffiliations(), equalTo(List.of(e11)));
    }

    private CreateRequest createRequestWithoutResourceUri(URI e11) {
        return new CreateRequest(
            e11,
            List.of(e11)
        );
    }
}
