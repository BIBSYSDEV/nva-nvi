package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.Map;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import no.unit.nva.stubs.FakeContext;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DeleteNviIndexHandlerTest {

  @Test
  void shouldDeleteIndex() {
    var appender = LogUtils.getTestingAppenderForRootLogger();
    var searchClient = mock(CandidateSearchClient.class);
    var handler = new DeleteNviIndexHandler(indexName -> searchClient);
    handler.handleRequest(null, new FakeContext());
    assertThat(appender.getMessages(), containsString("Deleted index:"));
  }

  @Test
  void shouldThrowExceptionAndLogWhenIndexDeletionFails() throws IOException {
    var searchClient = mock(CandidateSearchClient.class);
    Mockito.doThrow(new IOException()).when(searchClient).deleteIndex();
    var handler = new DeleteNviIndexHandler(indexName -> searchClient);
    assertThrows(RuntimeException.class, () -> handler.handleRequest(null, new FakeContext()));
  }

  @Test
  void shouldDeleteCustomIndexWhenIndexNameProvided() {
    var appender = LogUtils.getTestingAppenderForRootLogger();
    var defaultClient = mock(CandidateSearchClient.class);
    var customClient = mock(CandidateSearchClient.class);
    var handler =
        new DeleteNviIndexHandler(
            indexName -> NVI_CANDIDATES_INDEX.equals(indexName) ? defaultClient : customClient);
    handler.handleRequest(Map.of("indexName", "nvi-candidates-v2"), new FakeContext());
    assertThat(appender.getMessages(), containsString("nvi-candidates-v2"));
  }
}
