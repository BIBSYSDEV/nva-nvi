package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.events.batch.request.PaginationState.createInitialPaginationState;

import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.events.batch.job.BatchJob;
import no.sikt.nva.nvi.events.batch.job.ScanCandidatesJob;
import no.sikt.nva.nvi.events.batch.job.CandidatesByYearJob;
import no.sikt.nva.nvi.events.batch.job.RefreshPeriodsJob;
import no.sikt.nva.nvi.events.batch.job.StartCandidateScanJob;
import no.sikt.nva.nvi.events.batch.model.ReportingYearFilter;
import no.sikt.nva.nvi.events.batch.request.BatchJobRequest;
import no.sikt.nva.nvi.events.batch.request.CandidateScanRequest;
import no.sikt.nva.nvi.events.batch.request.CandidatesByYearRequest;
import no.sikt.nva.nvi.events.batch.request.StartBatchJobRequest;

public class BatchJobFactory {

  private final CandidateService candidateService;
  private final NviPeriodService periodService;

  public BatchJobFactory(CandidateService candidateService, NviPeriodService periodService) {
    this.candidateService = candidateService;
    this.periodService = periodService;
  }

  public BatchJob from(BatchJobRequest request) {
    return switch (request) {
      case StartBatchJobRequest newRequest -> handleInitialRequest(newRequest);
      case CandidateScanRequest continuationRequest ->
          handleScanRequest(continuationRequest);
      case CandidatesByYearRequest continuationRequest ->
          handleFilteredYearJob(continuationRequest);
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
      case null -> new StartCandidateScanJob(request);
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

  private ScanCandidatesJob handleScanRequest(CandidateScanRequest request) {
    return new ScanCandidatesJob(candidateService, request);
  }
}
