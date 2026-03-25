package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.ManageIndexAliasHandler.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Map;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ManageIndexAliasHandlerTest {

  private CandidateSearchClient searchClient;
  private ManageIndexAliasHandler handler;

  @BeforeEach
  void setUp() {
    searchClient = mock(CandidateSearchClient.class);
    handler = new ManageIndexAliasHandler(searchClient);
  }

  @Test
  void shouldUpdateAliasWhenValidInput() throws IOException {
    var input = Map.of("aliasName", "nvi", "targetIndex", "nvi-candidates-v2");

    var result = handler.handleRequest(input, null);

    assertEquals(SUCCESS, result);
    verify(searchClient).updateAlias("nvi", "nvi-candidates-v2");
  }

  @Test
  void shouldThrowWhenAliasNameMissing() {
    var input = Map.of("targetIndex", "nvi-candidates-v2");

    assertThrows(IllegalArgumentException.class, () -> handler.handleRequest(input, null));
  }

  @Test
  void shouldThrowWhenTargetIndexMissing() {
    var input = Map.of("aliasName", "nvi");

    assertThrows(IllegalArgumentException.class, () -> handler.handleRequest(input, null));
  }

  @Test
  void shouldThrowWhenEmptyInput() {
    assertThrows(IllegalArgumentException.class, () -> handler.handleRequest(Map.of(), null));
  }

  @Test
  void shouldThrowRuntimeExceptionWhenUpdateAliasFails() throws IOException {
    Mockito.doThrow(new IOException("OpenSearch error"))
        .when(searchClient)
        .updateAlias("nvi", "nvi-candidates-v2");
    var input = Map.of("aliasName", "nvi", "targetIndex", "nvi-candidates-v2");

    assertThrows(RuntimeException.class, () -> handler.handleRequest(input, null));
  }
}
