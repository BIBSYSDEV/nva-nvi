package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.DeleteNviIndexHandler.FINISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import no.unit.nva.stubs.FakeContext;
import nva.commons.logutils.LogRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DeleteNviIndexHandlerTest {

  @Test
  void shouldDeleteIndex() {
    var logRecorder = LogRecorder.forClass(DeleteNviIndexHandler.class);
    var searchClient = mock(CandidateSearchClient.class);
    var handler = new DeleteNviIndexHandler(searchClient);

    handler.handleRequest(null, new FakeContext());

    assertThat(logRecorder.messages()).anyMatch(message -> message.contains(FINISHED));
  }

  @Test
  void shouldThrowExceptionAndLogWhenIndexDeletionFails() throws IOException {
    var searchClient = mock(CandidateSearchClient.class);
    Mockito.doThrow(new IOException()).when(searchClient).deleteIndex();
    var handler = new DeleteNviIndexHandler(searchClient);
    assertThrows(RuntimeException.class, () -> handler.handleRequest(null, new FakeContext()));
  }
}
