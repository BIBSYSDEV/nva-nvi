package no.sikt.nva.nvi.events;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;

import java.util.List;
import no.sikt.nva.nvi.events.batch.model.BatchJobType;
import no.sikt.nva.nvi.events.batch.model.ReportingYearFilter;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;

public final class RequestFixtures {

  private RequestFixtures() {}

  public static StartBatchJobRequest refreshAllCandidates() {
    return StartBatchJobRequest.builder()
        .withJobType(BatchJobType.REFRESH_CANDIDATES)
        .withMaxParallelSegments(5)
        .build();
  }

  public static StartBatchJobRequest refreshCandidatesForYear(String... years) {
    return refreshAllCandidates()
        .copy()
        .withFilter(new ReportingYearFilter(List.of(years)))
        .build();
  }

  public static StartBatchJobRequest migrateCandidatesForCurrentYear() {
    return StartBatchJobRequest.builder()
        .withJobType(BatchJobType.MIGRATE_CANDIDATES)
        .withFilter(new ReportingYearFilter(List.of(String.valueOf(CURRENT_YEAR))))
        .withMaxParallelSegments(5)
        .build();
  }
}
