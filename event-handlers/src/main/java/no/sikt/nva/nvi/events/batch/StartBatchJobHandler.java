package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.service.CandidateService.defaultCandidateService;
import static no.sikt.nva.nvi.common.service.NviPeriodService.defaultNviPeriodService;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

// FIXME: Temporary suppression to split up PRs
@SuppressWarnings("PMD.UnusedPrivateField")
public class StartBatchJobHandler implements RequestHandler<StartBatchJobRequest, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartBatchJobHandler.class);
  private static final String PROCESSING_ENABLED = "PROCESSING_ENABLED";
  private static final String BATCH_JOB_QUEUE_URL = "BATCH_JOB_QUEUE_URL";
  private static final String EVENT_BUS_NAME = "EVENT_BUS_NAME";
  private static final String DETAIL_TYPE = "NviService.BatchJob.StartBatchJob";
  private static final String SOURCE = "nva.nvi.batch";
  private static final int SQS_BATCH_SIZE = 10;

  private final BatchJobProcessor batchJobProcessor;
  private final QueueClient queueClient;
  private final EventBridgeClient eventBridgeClient;
  private final String queueUrl;
  private final String eventBusName;
  private final boolean processingEnabled;

  @JacocoGenerated
  public StartBatchJobHandler() {
    this(
        defaultCandidateService(),
        defaultNviPeriodService(),
        defaultEventBridgeClient(),
        new Environment(),
        new NviQueueClient());
  }

  public StartBatchJobHandler(
      CandidateService candidateService,
      NviPeriodService periodService,
      EventBridgeClient eventBridgeClient,
      Environment environment,
      QueueClient queueClient) {
    this.batchJobProcessor = new BatchJobProcessor(candidateService, periodService);
    this.eventBridgeClient = eventBridgeClient;
    this.queueClient = queueClient;
    this.queueUrl = environment.readEnv(BATCH_JOB_QUEUE_URL);
    this.eventBusName = environment.readEnv(EVENT_BUS_NAME);
    this.processingEnabled = Boolean.parseBoolean(environment.readEnv(PROCESSING_ENABLED));
  }

  @JacocoGenerated
  private static EventBridgeClient defaultEventBridgeClient() {
    return EventBridgeClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
  }

  @Override
  public Void handleRequest(StartBatchJobRequest input, Context context) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
