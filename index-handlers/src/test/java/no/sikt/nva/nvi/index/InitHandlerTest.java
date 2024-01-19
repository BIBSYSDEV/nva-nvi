package no.sikt.nva.nvi.index;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InitHandlerTest {

    private OpenSearchClient openSearchClient;
    private InitHandler handler;

    @BeforeEach
    void setup() {
        openSearchClient = mock(OpenSearchClient.class);
        handler = new InitHandler(openSearchClient);
    }

    @Test
    void shouldCreateIndexWhenNotExisting() {
        when(openSearchClient.indexExists()).thenReturn(false);

        handler.handleRequest(null, null);

        verify(openSearchClient).createIndex();
    }

    @Test
    void shouldNotCreateIndexWhenExisting() {
        when(openSearchClient.indexExists()).thenReturn(true);

        handler.handleRequest(null, null);

        verify(openSearchClient, never()).createIndex();
    }
}
