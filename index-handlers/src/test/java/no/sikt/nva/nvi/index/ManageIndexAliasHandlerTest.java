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

  private static final String ALIAS_NAME = "nvi";
  private static final String TARGET_INDEX = "nvi-candidates-v2";
  private CandidateSearchClient searchClient;
  private ManageIndexAliasHandler handler;

  @BeforeEach
  void setUp() {
    searchClient = mock(CandidateSearchClient.class);
    handler = new ManageIndexAliasHandler(searchClient);
  }

  @Test
  void shouldUpdateAliasWhenValidInput() throws IOException {
    var input = Map.of("aliasName", ALIAS_NAME, "targetIndex", TARGET_INDEX);

    var result = handler.handleRequest(input, null);

    assertEquals(SUCCESS, result);
    verify(searchClient).updateAlias(ALIAS_NAME, TARGET_INDEX);
  }

  @Test
  void shouldThrowWhenAliasNameMissing() {
    var input = Map.of("targetIndex", TARGET_INDEX);

    assertThrows(IllegalArgumentException.class, () -> handler.handleRequest(input, null));
  }

  @Test
  void shouldThrowWhenTargetIndexMissing() {
    var input = Map.of("aliasName", ALIAS_NAME);

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
        .updateAlias(ALIAS_NAME, TARGET_INDEX);
    var input = Map.of("aliasName", ALIAS_NAME, "targetIndex", TARGET_INDEX);

    assertThrows(RuntimeException.class, () -> handler.handleRequest(input, null));
  }
}
