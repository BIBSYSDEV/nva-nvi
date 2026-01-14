package no.sikt.nva.nvi.events.batch;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
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

  public BatchJobResult process(StartBatchJobRequest input) {
    if (input.isInitialInvocation()) {
      return handleInitialInvocation(input);
    } else {
      return handleSegmentProcessing(input);
    }
  }

  private BatchJobResult handleInitialInvocation(StartBatchJobRequest input) {
    LOGGER.info("Processing initial invocation");
    if (BatchJobType.REFRESH_PERIODS.equals(input.jobType())) {
      return generatePeriodMessages(input);
    } else if (input.hasYearFilter()) {
      return generateYearQueryEvent(input);
    } else {
      return generateTableScanEvents(input);
    }
  }

  /** Directly generates work items for all periods because there are few of them. */
  private BatchJobResult generatePeriodMessages(StartBatchJobRequest input) {
    var messages =
        periodService.getAll().stream()
            .map(NviPeriod::publishingYear)
            .map(String::valueOf)
            .map(RefreshPeriodMessage::new)
            .map(BatchJobMessage.class::cast)
            .limit(input.maxRemainingItems())
            .toList();
    return new BatchJobResult(messages, null);
  }

  /** Generates events to run a parallelized table scan for candidates. */
  private BatchJobResult generateTableScanEvents(StartBatchJobRequest input) {
    var totalSegments = input.maxParallelSegments();
    var events =
        IntStream.range(0, totalSegments)
            .mapToObj(segment -> TableScanState.forSegment(segment, totalSegments))
            .map(nextState -> input.copy().withPaginationState(nextState).build())
            .toList();
    return new BatchJobResult(null, events);
  }

  /** Generates an event to run a sequential scan for candidates by reporting year. */
  private BatchJobResult generateYearQueryEvent(StartBatchJobRequest input) {
    var yearFilter = (ReportingYearFilter) input.filter();
    var yearQueryState = YearQueryState.forYears(yearFilter.reportingYears());
    var events = input.copy().withPaginationState(yearQueryState).build();
    return new BatchJobResult(null, List.of(events));
  }

  private BatchJobResult handleSegmentProcessing(StartBatchJobRequest input) {
    var nextBatchSize = Integer.min(input.maxRemainingItems(), DEFAULT_PAGE_SIZE);
    if (nextBatchSize <= 0) {
      LOGGER.info("Item limit reached, stopping processing");
      return new BatchJobResult(emptyList(), emptyList());
    } else {
      LOGGER.info("Processing segment");
      var paginationState = input.paginationState();
      return switch (paginationState) {
        case YearQueryState state -> createCandidateMessages(input, state, nextBatchSize);
        case TableScanState state -> generateCandidateMessages(input, state, nextBatchSize);
      };
    }
  }

  /** Creates work items for candidates by year from GSI query */
  private BatchJobResult createCandidateMessages(
      StartBatchJobRequest input, YearQueryState state, int nextBatchSize) {
    var year = state.currentYear();
    LOGGER.info("Processing year query for year: {}", year);

    var listingResult =
        candidateService.listCandidateIdentifiersByYear(
            year, nextBatchSize, state.lastEvaluatedKey());

    var messages =
        listingResult.getDatabaseEntries().stream()
            .map(identifier -> createMessage(input.jobType(), identifier))
            .toList();

    if (listingResult.shouldContinueScan()) {
      LOGGER.info("Continuing scan for current year");
      var nextState =
          state.withNextPage(
              listingResult.getStartMarker(), listingResult.getDatabaseEntries().size());
      var nextEvent = input.copy().withPaginationState(nextState).build();
      return new BatchJobResult(messages, List.of(nextEvent));
    } else if (state.hasMoreYears()) {
      LOGGER.info("Continuing scan for next year");
      var nextState = state.withNextYear();
      var nextEvent = input.copy().withPaginationState(nextState).build();
      return new BatchJobResult(messages, List.of(nextEvent)); // TODO: Single return point
    }
    return new BatchJobResult(messages, null); // TODO: emptyList instead of null?
  }

  /** Creates work items for candidates with parallelized table scan */
  private BatchJobResult generateCandidateMessages(
      StartBatchJobRequest input, TableScanState state, int nextBatchSize) {
    LOGGER.info("Processing scan query for segment: {}", state.segment());

    var listingResult =
        candidateService.listCandidateIdentifiers(
            state.segment(), state.totalSegments(), nextBatchSize, state.lastEvaluatedKey());

    var messages =
        listingResult.getDatabaseEntries().stream()
            .map(identifier -> createMessage(input.jobType(), identifier))
            .toList();

    if (listingResult.shouldContinueScan()) {
      LOGGER.info("Continuing table scan");
      var nextState =
          state.withNextPage(
              listingResult.getStartMarker(), listingResult.getDatabaseEntries().size());
      var nextEvent = input.copy().withPaginationState(nextState).build();
      return new BatchJobResult(messages, List.of(nextEvent));
    } else {
      LOGGER.info("Table scan completed for segment");
      return new BatchJobResult(messages, null); // TODO: emptyList instead of null?
    }
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
