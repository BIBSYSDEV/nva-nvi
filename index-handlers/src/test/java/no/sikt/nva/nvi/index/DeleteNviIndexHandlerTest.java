package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.DeleteNviIndexHandler.FINISHED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.IOException;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DeleteNviIndexHandlerTest {

  @Test
  void shouldDeleteIndex() {
    var appender = LogUtils.getTestingAppenderForRootLogger();
    var searchClient = mock(OpenSearchClient.class);
    var handler = new DeleteNviIndexHandler(searchClient);
    handler.handleRequest(null, mock(Context.class));
    assertThat(appender.getMessages(), containsString(FINISHED));
  }

  @Test
  void shouldThrowExceptionAndLogWhenIndexDeletionFails() throws IOException {
    var openSearchClient = mock(OpenSearchClient.class);
    Mockito.doThrow(new IOException()).when(openSearchClient).deleteIndex();
    var handler = new DeleteNviIndexHandler(openSearchClient);
    assertThrows(RuntimeException.class, () -> handler.handleRequest(null, mock(Context.class)));
  }
}
