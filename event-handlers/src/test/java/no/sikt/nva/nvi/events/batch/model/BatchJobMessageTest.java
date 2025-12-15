package no.sikt.nva.nvi.events.batch.model;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.stream.Stream;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BatchJobMessageTest {

  @ParameterizedTest
  @MethodSource("batchJobMessageProvider")
  void shouldHandleRoundTripConversion(BatchJobMessage originalMessage) {
    var jsonString = originalMessage.toJsonString();
    var roundTrippedMessage = fromString(jsonString);

    assertThat(roundTrippedMessage).isEqualTo(originalMessage);
  }

  private static Stream<Arguments> batchJobMessageProvider() {
    return Stream.of(
        argumentSet("RefreshCandidateMessage", new RefreshCandidateMessage(randomUUID())),
        argumentSet("MigrateCandidateMessage", new MigrateCandidateMessage(randomUUID())),
        argumentSet("RefreshPeriodMessage", new RefreshPeriodMessage(randomYear())));
  }

  private static BatchJobMessage fromString(String json) {
    try {
      return JsonUtils.dtoObjectMapper.readValue(json, BatchJobMessage.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
