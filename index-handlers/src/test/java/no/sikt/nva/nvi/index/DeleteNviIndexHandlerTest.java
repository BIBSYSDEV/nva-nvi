package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.DeleteNviIndexHandler.FINISHED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.IOException;
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
    var handler = new DeleteNviIndexHandler(searchClient);
    handler.handleRequest(null, new FakeContext());
    assertThat(appender.getMessages(), containsString(FINISHED));
  }

  @Test
  void shouldThrowExceptionAndLogWhenIndexDeletionFails() throws IOException {
    var searchClient = mock(CandidateSearchClient.class);
    Mockito.doThrow(new IOException()).when(searchClient).deleteIndex();
    var handler = new DeleteNviIndexHandler(searchClient);
    assertThrows(RuntimeException.class, () -> handler.handleRequest(null, new FakeContext()));
  }
}
