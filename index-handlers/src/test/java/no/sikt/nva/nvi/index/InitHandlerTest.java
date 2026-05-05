package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InitHandlerTest {

  private CandidateSearchClient defaultClient;
  private CandidateSearchClient customClient;
  private InitHandler handler;

  @BeforeEach
  void setup() {
    defaultClient = mock(CandidateSearchClient.class);
    customClient = mock(CandidateSearchClient.class);
    handler =
        new InitHandler(
            indexName -> NVI_CANDIDATES_INDEX.equals(indexName) ? defaultClient : customClient);
  }

  @Test
  void shouldCreateIndexWhenNotExisting() {
    when(defaultClient.indexExists()).thenReturn(false);

    handler.handleRequest(null, null);

    verify(defaultClient).createIndex();
  }

  @Test
  void shouldNotCreateIndexWhenExisting() {
    when(defaultClient.indexExists()).thenReturn(true);

    handler.handleRequest(null, null);

    verify(defaultClient, never()).createIndex();
  }

  @Test
  void shouldCreateCustomIndexWhenIndexNameProvided() {
    when(customClient.indexExists()).thenReturn(false);

    var result = handler.handleRequest(Map.of("indexName", "nvi-candidates-v2"), null);

    verify(customClient).createIndex();
    assertEquals(InitHandler.SUCCESS, result);
  }

  @Test
  void shouldUseDefaultIndexWhenEmptyInputProvided() {
    when(defaultClient.indexExists()).thenReturn(false);

    handler.handleRequest(Map.of(), null);

    verify(defaultClient).createIndex();
  }

  @Test
  void shouldUseDefaultIndexWhenNullInputProvided() {
    when(defaultClient.indexExists()).thenReturn(false);

    handler.handleRequest(null, null);

    verify(defaultClient).createIndex();
  }
}
