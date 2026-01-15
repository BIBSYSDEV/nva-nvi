package no.sikt.nva.nvi.events.batch;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.events.batch.model.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.model.BatchJobResult;
import no.sikt.nva.nvi.events.batch.model.BatchJobType;
import no.sikt.nva.nvi.events.batch.model.MigrateCandidateMessage;
import no.sikt.nva.nvi.events.batch.model.RefreshCandidateMessage;
import no.sikt.nva.nvi.events.batch.model.RefreshPeriodMessage;
import no.sikt.nva.nvi.events.batch.model.ReportingYearFilter;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;
import no.sikt.nva.nvi.events.batch.model.TableScanState;
import no.sikt.nva.nvi.events.batch.model.YearQueryState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchJobProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobProcessor.class);
  private static final int DEFAULT_PAGE_SIZE = 700;

  private final CandidateService candidateService;
  private final NviPeriodService periodService;

  public BatchJobProcessor(CandidateService candidateService, NviPeriodService periodService) {
    this.candidateService = candidateService;
    this.periodService = periodService;
  }

  public BatchJobResult process(StartBatchJobRequest request) {
    if (request.isInitialInvocation()) {
      return handleInitialInvocation(request);
    } else {
      return handleSegmentProcessing(request);
    }
  }

  private BatchJobResult handleInitialInvocation(StartBatchJobRequest request) {
    LOGGER.info("Processing initial invocation");
    if (BatchJobType.REFRESH_PERIODS.equals(request.jobType())) {
      return createMessagesForAllPeriods(request);
    } else if (request.hasYearFilter()) {
      return createInitialScanByYearEvent(request);
    } else {
      return createInitialTableScanEvents(request);
    }
  }

  private BatchJobResult handleSegmentProcessing(StartBatchJobRequest request) {
    var nextBatchSize = Integer.min(request.maxRemainingItems(), DEFAULT_PAGE_SIZE);
    if (nextBatchSize <= 0) {
      LOGGER.info("Item limit reached, stopping processing");
      return new BatchJobResult(emptyList(), emptyList());
    }
    LOGGER.info("Processing segment");
    return createCandidateMessages(request, nextBatchSize);
  }

  private BatchJobResult createCandidateMessages(StartBatchJobRequest request, int nextBatchSize) {
    return switch (request.paginationState()) {
      case YearQueryState state -> createCandidateMessages(request, state, nextBatchSize);
      case TableScanState state -> createCandidateMessages(request, state, nextBatchSize);
    };
  }

  private BatchJobResult createMessagesForAllPeriods(StartBatchJobRequest request) {
    var messages =
        periodService.getAll().stream()
            .map(NviPeriod::publishingYear)
            .map(String::valueOf)
            .map(RefreshPeriodMessage::new)
            .map(BatchJobMessage.class::cast)
            .limit(request.maxRemainingItems())
            .toList();
    return new BatchJobResult(messages, emptyList());
  }

  /** Generates events to run a parallelized table scan for candidates. */
  private BatchJobResult createInitialTableScanEvents(StartBatchJobRequest request) {
    var totalSegments = request.maxParallelSegments();
    var events =
        IntStream.range(0, totalSegments)
            .mapToObj(segment -> TableScanState.forSegment(segment, totalSegments))
            .map(nextState -> request.copy().withPaginationState(nextState).build())
            .toList();
    return new BatchJobResult(emptyList(), events);
  }

  private List<StartBatchJobRequest> createTableScanContinuationEvents(
      StartBatchJobRequest request, TableScanState state, ListingResult<UUID> listingResult) {
    if (listingResult.shouldContinueScan()) {
      LOGGER.info("Continuing table scan");
      var nextState =
          state.withNextPage(
              listingResult.getStartMarker(), listingResult.getDatabaseEntries().size());
      return List.of(request.copy().withPaginationState(nextState).build());
    }
    LOGGER.info("Table scan completed for segment");
    return emptyList();
  }

  /** Creates work items for candidates with parallelized table scan */
  private BatchJobResult createCandidateMessages(
      StartBatchJobRequest request, TableScanState state, int nextBatchSize) {
    LOGGER.info("Processing scan query for segment: {}", state.segment());

    var listingResult =
        candidateService.listCandidateIdentifiers(
            state.segment(), state.totalSegments(), nextBatchSize, state.lastEvaluatedKey());

    var messages = toMessages(request, listingResult);
    var continuationEvents = createTableScanContinuationEvents(request, state, listingResult);

    return new BatchJobResult(messages, continuationEvents);
  }

  /** Generates an event to run a sequential scan for candidates by reporting year. */
  private BatchJobResult createInitialScanByYearEvent(StartBatchJobRequest request) {
    var yearFilter = (ReportingYearFilter) request.filter();
    var yearQueryState = YearQueryState.forYears(yearFilter.reportingYears());
    var events = request.copy().withPaginationState(yearQueryState).build();
    return new BatchJobResult(emptyList(), List.of(events));
  }

  private List<StartBatchJobRequest> createScanByYearContinuationEvents(
      StartBatchJobRequest request, YearQueryState state, ListingResult<UUID> listingResult) {
    if (listingResult.shouldContinueScan()) {
      LOGGER.info("Continuing scan for current year");
      var nextState =
          state.withNextPage(
              listingResult.getStartMarker(), listingResult.getDatabaseEntries().size());
      return List.of(request.copy().withPaginationState(nextState).build());
    }
    if (state.hasMoreYears()) {
      LOGGER.info("Continuing scan for next year");
      var nextState = state.withNextYear();
      return List.of(request.copy().withPaginationState(nextState).build());
    }
    return emptyList();
  }

  /** Creates work items for candidates by year from GSI query */
  private BatchJobResult createCandidateMessages(
      StartBatchJobRequest request, YearQueryState state, int nextBatchSize) {
    var year = state.currentYear();
    LOGGER.info("Processing year query for year: {}", year);

    var listingResult =
        candidateService.listCandidateIdentifiersByYear(
            year, nextBatchSize, state.lastEvaluatedKey());

    var messages = toMessages(request, listingResult);
    var continuationEvents = createScanByYearContinuationEvents(request, state, listingResult);

    return new BatchJobResult(messages, continuationEvents);
  }

  private List<BatchJobMessage> toMessages(
      StartBatchJobRequest request, ListingResult<UUID> listingResult) {
    return listingResult.getDatabaseEntries().stream()
        .map(identifier -> createMessage(request.jobType(), identifier))
        .toList();
  }

  private BatchJobMessage createMessage(BatchJobType jobType, UUID identifier) {
    return switch (jobType) {
      case REFRESH_CANDIDATES -> new RefreshCandidateMessage(identifier);
      case MIGRATE_CANDIDATES -> new MigrateCandidateMessage(identifier);
      case REFRESH_PERIODS ->
          throw new UnsupportedOperationException("REFRESH_PERIODS messages are created directly");
    };
  }
}
