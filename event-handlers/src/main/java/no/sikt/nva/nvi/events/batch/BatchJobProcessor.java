package no.sikt.nva.nvi.events.batch;

import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchJobProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobProcessor.class);

  private final CandidateService candidateService;
  private final NviPeriodService periodService;

  public BatchJobProcessor(CandidateService candidateService, NviPeriodService periodService) {
    this.candidateService = candidateService;
    this.periodService = periodService;
  }
}
