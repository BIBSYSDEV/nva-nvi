package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.events.batch.request.PaginationState.createInitialPaginationState;

import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.events.batch.job.BatchJob;
import no.sikt.nva.nvi.events.batch.job.CandidateScanJob;
import no.sikt.nva.nvi.events.batch.job.CandidatesByYearJob;
import no.sikt.nva.nvi.events.batch.job.RefreshPeriodsJob;
import no.sikt.nva.nvi.events.batch.job.StartParallelScanJob;
import no.sikt.nva.nvi.events.batch.model.ReportingYearFilter;
import no.sikt.nva.nvi.events.batch.request.BatchJobRequest;
import no.sikt.nva.nvi.events.batch.request.CandidateScanBatchJobRequest;
import no.sikt.nva.nvi.events.batch.request.CandidatesByYearRequest;
import no.sikt.nva.nvi.events.batch.request.StartBatchJobRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchJobFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobFactory.class);

  private final CandidateService candidateService;
  private final NviPeriodService periodService;

  public BatchJobFactory(CandidateService candidateService, NviPeriodService periodService) {
    this.candidateService = candidateService;
    this.periodService = periodService;
  }

  public BatchJob from(BatchJobRequest request) {
    // Batch job types:
    // 1. Initial invocation - REFRESH_PERIODS -> create single job for periods
    // 2. Initial invocation (filter by year) -> create query by year job
    // 3. Initial invocation (no filter) -> split to parallel scan jobs
    // 4. Query by year job
    // 5. Scan segment job
    // TODO: Check terminal state here?
    return switch (request) {
      case StartBatchJobRequest newRequest -> handleInitialRequest(newRequest);
      case CandidateScanBatchJobRequest continuationRequest ->
          handleScanRequest(continuationRequest);
      case CandidatesByYearRequest continuationRequest ->
          handleFilteredYearJob(continuationRequest);
      default -> throw new IllegalStateException("Unexpected value: " + request);
    };
  }

  private BatchJob handleInitialRequest(StartBatchJobRequest request) {
    return switch (request.jobType()) {
      case REFRESH_PERIODS -> createRefreshPeriodsJob(periodService, request);
      case REFRESH_CANDIDATES, MIGRATE_CANDIDATES -> createCandidateJob(request);
    };
  }

  private RefreshPeriodsJob createRefreshPeriodsJob(
      NviPeriodService periodService, StartBatchJobRequest request) {
    return new RefreshPeriodsJob(periodService, request.maxItems(), request.filter());
  }

  private BatchJob createCandidateJob(StartBatchJobRequest request) {
    return switch (request.filter()) {
      case ReportingYearFilter yearFilter -> createLoadCandidatesByYearJob(request, yearFilter);
      case null -> new StartParallelScanJob(request);
    };
  }

  private BatchJob createLoadCandidatesByYearJob(
      StartBatchJobRequest request, ReportingYearFilter yearFilter) {
    var paginationState = createInitialPaginationState(request.batchSize(), request.maxItems());
    var initialJobRequest =
        new CandidatesByYearRequest(request.jobType(), yearFilter, paginationState);
    return new CandidatesByYearJob(candidateService, initialJobRequest);
  }

  private CandidatesByYearJob handleFilteredYearJob(CandidatesByYearRequest request) {
    return new CandidatesByYearJob(candidateService, request);
  }

  private CandidateScanJob handleScanRequest(CandidateScanBatchJobRequest request) {
    return new CandidateScanJob(candidateService, request);
  }
}
