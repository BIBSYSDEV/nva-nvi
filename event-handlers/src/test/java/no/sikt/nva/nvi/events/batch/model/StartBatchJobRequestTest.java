package no.sikt.nva.nvi.events.batch.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StartBatchJobRequestTest {

  @Nested
  class SerializationTests {

    @Test
    void shouldDeserializeMinimalRequestFromAwsConsole() {
      var json =
          """
          { "type": "StartBatchJobRequest", "jobType": "REFRESH_CANDIDATES" }
          """;

      var request = fromJson(json);
      var expectedRequest =
          StartBatchJobRequest.builder().withJobType(BatchJobType.REFRESH_CANDIDATES).build();

      assertThat(request).isEqualTo(expectedRequest);
      assertThat(request.isInitialInvocation()).isTrue();
      assertThat(request.hasItemLimit()).isFalse();
      assertThat(request.hasYearFilter()).isFalse();
    }

    @Test
    void shouldDeserializeFullRequestFromAwsConsole() {
      var json =
          """
          {
            "type": "StartBatchJobRequest",
            "jobType": "MIGRATE_CANDIDATES",
            "filter": {
              "type": "ReportingYearFilter",
              "reportingYears": ["2024", "2025"]
            },
            "maxParallelSegments": 5,
            "maxItemsPerSegment": 100
          }
          """;

      var request = fromJson(json);
      var expectedRequest =
          StartBatchJobRequest.builder()
              .withJobType(BatchJobType.MIGRATE_CANDIDATES)
              .withFilter(new ReportingYearFilter(List.of("2024", "2025")))
              .withMaxItemsPerSegment(100)
              .withMaxParallelSegments(5)
              .build();

      assertThat(request).isEqualTo(expectedRequest);
    }

    private static StartBatchJobRequest fromJson(String json) {
      try {
        return JsonUtils.dtoObjectMapper.readValue(json, StartBatchJobRequest.class);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
