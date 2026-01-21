package no.sikt.nva.nvi.events.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BatchJobExceptionTest {

  @Test
  void shouldStoreMessage() {
    var message = "Something went wrong";
    var exception = new BatchJobException(message);
    assertThat(exception.getMessage()).isEqualTo(message);
  }

  @Test
  void shouldBeRuntimeException() {
    var exception = new BatchJobException("error");
    assertThat(exception).isInstanceOf(RuntimeException.class);
  }
}
