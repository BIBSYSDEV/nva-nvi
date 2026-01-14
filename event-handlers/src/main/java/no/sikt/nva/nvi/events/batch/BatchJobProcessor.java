package no.sikt.nva.nvi.events.batch;

import static java.util.Collections.emptyList;

import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.events.batch.model.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.model.BatchJobResult;
import no.sikt.nva.nvi.events.batch.model.BatchJobType;
import no.sikt.nva.nvi.events.batch.model.RefreshPeriodMessage;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: Temporary suppression to split up PRs
@SuppressWarnings("PMD.UnusedPrivateField")
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
      throw new UnsupportedOperationException("Not implemented yet");
    }
  }

  private BatchJobResult handleInitialInvocation(StartBatchJobRequest input) {
    LOGGER.info("Processing initial invocation");
    if (BatchJobType.REFRESH_PERIODS.equals(input.jobType())) {
      return createPeriodMessages(input);
    } else {
      throw new UnsupportedOperationException("Not implemented yet");
    }
  }

  /** Directly generates work items for all periods because there are few of them. */
  private BatchJobResult createPeriodMessages(StartBatchJobRequest input) {
    var messages =
        periodService.getAll().stream()
            .map(NviPeriod::publishingYear)
            .map(String::valueOf)
            .map(RefreshPeriodMessage::new)
            .map(BatchJobMessage.class::cast)
            .limit(input.maxRemainingItems())
            .toList();
    return new BatchJobResult(messages, emptyList());
  }
}
