package no.sikt.nva.nvi.events.batch.model;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    @ParameterizedTest
    @MethodSource("requestProvider")
    void shouldHandleRoundTripConversion(StartBatchJobRequest original) {
      var deserialized = fromJson(original.toJsonString());
      assertThat(deserialized).isEqualTo(original);
    }

    private static Stream<Arguments> requestProvider() {
      return Stream.of(
          argumentSet(
              "with TableScanState",
              StartBatchJobRequest.builder()
                  .withJobType(BatchJobType.REFRESH_CANDIDATES)
                  .withMaxItemsPerSegment(100)
                  .withPaginationState(new TableScanState(2, 10, fakeCandidateKey(1), 50))
                  .build()),
          argumentSet(
              "with YearQueryState",
              StartBatchJobRequest.builder()
                  .withJobType(BatchJobType.MIGRATE_CANDIDATES)
                  .withFilter(new ReportingYearFilter(List.of("2023", "2024")))
                  .withMaxItemsPerSegment(50)
                  .withPaginationState(
                      new YearQueryState(List.of("2024"), fakeCandidateKey(2), 200))
                  .build()));
    }

    private static StartBatchJobRequest fromJson(String json) {
      try {
        return JsonUtils.dtoObjectMapper.readValue(json, StartBatchJobRequest.class);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Nested
  class PaginationTests {
    private static final String THIS_YEAR = String.valueOf(CURRENT_YEAR);
    private static final String LAST_YEAR = String.valueOf(CURRENT_YEAR - 1);

    @Test
    void shouldCreateContinuationWithUpdatedPaginationState() {
      var original =
          StartBatchJobRequest.builder()
              .withJobType(BatchJobType.REFRESH_CANDIDATES)
              .withFilter(new ReportingYearFilter(List.of(THIS_YEAR)))
              .withMaxItemsPerSegment(100)
              .withMaxParallelSegments(5)
              .build();

      var continuation =
          original.copy().withPaginationState(TableScanState.forSegment(2, 5)).build();

      assertThat(continuation.jobType()).isEqualTo(original.jobType());
      assertThat(continuation.filter()).isEqualTo(original.filter());
      assertThat(continuation.maxItemsPerSegment()).isEqualTo(original.maxItemsPerSegment());
      assertThat(continuation.paginationState()).isNotNull();
    }

    @Test
    void tableScanStateShouldTrackPaginationWithinSegment() {
      var state = TableScanState.forSegment(3, 10);

      assertThat(state.segment()).isEqualTo(3);
      assertThat(state.totalSegments()).isEqualTo(10);
      assertThat(state.lastEvaluatedKey()).isNull();

      var lastEvaluatedKey = fakeCandidateKey(100);
      var afterPage = state.withNextPage(lastEvaluatedKey, 100);

      assertThat(afterPage.segment()).isEqualTo(3);
      assertThat(afterPage.itemsEnqueued()).isEqualTo(100);
      assertThat(afterPage.lastEvaluatedKey()).isEqualTo(lastEvaluatedKey);
    }

    @Test
    void yearQueryStateShouldTrackPaginationAcrossYears() {
      var firstState = YearQueryState.forYears(List.of(LAST_YEAR, THIS_YEAR));
      var expectedFirstState = new YearQueryState(List.of(LAST_YEAR, THIS_YEAR), emptyMap(), 0);
      assertThat(firstState).isEqualTo(expectedFirstState);

      var secondState = firstState.withNextPage(fakeCandidateKey(1), 100);
      assertThat(secondState.currentYear()).isEqualTo(LAST_YEAR);
      assertThat(firstState.hasMoreYears()).isTrue();
      assertThat(secondState.itemsEnqueued()).isEqualTo(100);
      assertThat(secondState.lastEvaluatedKey()).isEqualTo(fakeCandidateKey(1));

      var thirdState = firstState.withNextYear();
      assertThat(thirdState.currentYear()).isEqualTo(THIS_YEAR);
      assertThat(thirdState.lastEvaluatedKey()).isEmpty();
      assertThat(thirdState.hasMoreYears()).isFalse();
    }
  }

  private static Map<String, String> fakeCandidateKey(int candidateIdentifier) {
    var key = String.format("CANDIDATE#%d", candidateIdentifier);
    return Map.of("PK", key, "SK", key);
  }
}
