package no.sikt.nva.nvi.events.batch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: Temporary suppression to split up PRs
@SuppressWarnings("PMD.UnusedPrivateField")
public class StartBatchJobHandler implements RequestHandler<StartBatchJobRequest, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartBatchJobHandler.class);
  private final CandidateService candidateService;
  private final QueueClient queueClient;

  @JacocoGenerated
  public StartBatchJobHandler() {
    this(CandidateService.defaultCandidateService(), new NviQueueClient());
  }

  public StartBatchJobHandler(CandidateService candidateService, QueueClient queueClient) {
    this.candidateService = candidateService;
    this.queueClient = queueClient;
  }

  @Override
  public Void handleRequest(StartBatchJobRequest input, Context context) {
    LOGGER.info("Processing batch job: {}", input);
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
