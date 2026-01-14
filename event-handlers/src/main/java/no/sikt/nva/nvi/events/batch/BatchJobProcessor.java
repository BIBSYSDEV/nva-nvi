package no.sikt.nva.nvi.events.batch;


import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.events.batch.model.BatchJobResult;
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
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
