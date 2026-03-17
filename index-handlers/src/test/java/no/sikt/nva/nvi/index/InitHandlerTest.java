package no.sikt.nva.nvi.index;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InitHandlerTest {

  private CandidateSearchClient searchClient;
  private InitHandler handler;

  @BeforeEach
  void setup() {
    searchClient = mock(CandidateSearchClient.class);
    handler = new InitHandler(searchClient);
  }

  @Test
  void shouldCreateIndexWhenNotExisting() {
    when(searchClient.indexExists()).thenReturn(false);

    handler.handleRequest(null, null);

    verify(searchClient).createIndex();
  }

  @Test
  void shouldNotCreateIndexWhenExisting() {
    when(searchClient.indexExists()).thenReturn(true);

    handler.handleRequest(null, null);

    verify(searchClient, never()).createIndex();
  }
}
