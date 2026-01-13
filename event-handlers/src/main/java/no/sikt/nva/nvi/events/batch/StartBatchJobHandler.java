package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.common.service.NviPeriodService.defaultNviPeriodService;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
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
  private static final String DETAIL_TYPE = "StartBatchJob";
  private static final String SOURCE = "nva.nvi.batch";
  private static final int DEFAULT_PAGE_SIZE = 700;
  private static final int SQS_BATCH_SIZE = 10;

  private final CandidateRepository candidateRepository;
  private final NviPeriodService periodService;
  private final QueueClient queueClient;
  private final EventBridgeClient eventBridgeClient;
  private final String queueUrl;
  private final String eventBusName;
  private final boolean processingEnabled;

  @JacocoGenerated
  public StartBatchJobHandler() {
    this(
        new CandidateRepository(defaultDynamoClient()),
        defaultNviPeriodService(),
        new NviQueueClient(),
        defaultEventBridgeClient(),
        new Environment());
  }

  public StartBatchJobHandler(
      CandidateRepository candidateRepository,
      NviPeriodService periodService,
      QueueClient queueClient,
      EventBridgeClient eventBridgeClient,
      Environment environment) {
    this.candidateRepository = candidateRepository;
    this.periodService = periodService;
    this.queueClient = queueClient;
    this.eventBridgeClient = eventBridgeClient;
    this.queueUrl = environment.readEnv(BATCH_JOB_QUEUE_URL);
    this.eventBusName = environment.readEnv(EVENT_BUS_NAME);
    this.processingEnabled = Boolean.parseBoolean(environment.readEnv(PROCESSING_ENABLED));
  }

  @Override
  public Void handleRequest(StartBatchJobRequest input, Context context) {
    LOGGER.info("Processing batch job: {}", input);
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @JacocoGenerated
  private static EventBridgeClient defaultEventBridgeClient() {
    return EventBridgeClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
  }
}
