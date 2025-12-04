package cucumber.contexts;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getEvaluateNviCandidateHandlerEnvironment;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getUpsertNviCandidateHandlerEnvironment;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.util.List;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.events.evaluator.EvaluateNviCandidateHandler;
import no.sikt.nva.nvi.events.evaluator.EvaluatorService;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.events.persist.UpsertNviCandidateHandler;
import no.sikt.nva.nvi.test.SampleExpandedPublication;

public class EvaluationContext {
  private static final Context EVALUATION_HANDLER_CONTEXT = mock(Context.class);
  private static final Context UPSERT_HANDLER_CONTEXT = mock(Context.class);

  private final EvaluateNviCandidateHandler evaluateNviCandidateHandler;
  private final UpsertNviCandidateHandler upsertNviCandidateHandler;
  private final FakeSqsClient evaluationOutputQueue;
  private final FakeSqsClient upsertErrorQueue;
  private final TestScenario scenario;
  private Instant lastEvaluationStartedAt;

  public EvaluationContext(TestScenario scenario) {
    this.scenario = scenario;
    evaluationOutputQueue = new FakeSqsClient();
    upsertErrorQueue = new FakeSqsClient();
    evaluateNviCandidateHandler = createEvaluateNviCandidateHandler();
    upsertNviCandidateHandler = createUpsertNviCandidateHandler();
  }

  private EvaluateNviCandidateHandler createEvaluateNviCandidateHandler() {
    var environment = getEvaluateNviCandidateHandlerEnvironment();
    var creatorVerificationUtil =
        new CreatorVerificationUtil(scenario.getMockedAuthorizedBackendUriRetriever(), environment);
    var evaluatorService =
        new EvaluatorService(
            scenario.getS3StorageReaderForExpandedResourcesBucket(),
            creatorVerificationUtil,
            scenario.getCandidateService());

    return new EvaluateNviCandidateHandler(evaluatorService, evaluationOutputQueue, environment);
  }

  private UpsertNviCandidateHandler createUpsertNviCandidateHandler() {
    return new UpsertNviCandidateHandler(
        scenario.getCandidateService(),
        upsertErrorQueue,
        getUpsertNviCandidateHandlerEnvironment());
  }

  public void evaluatePublicationAndPersistResult(SampleExpandedPublication publication) {
    evaluatePublicationAndPersistResult(publication.toJsonString());
  }

  public void evaluatePublicationAndPersistResult(String publicationJson) {
    lastEvaluationStartedAt = Instant.now();
    var fileUri = scenario.setupExpandedPublicationInS3(publicationJson);
    var evaluationEvent = createEvaluationEvent(new PersistedResourceMessage(fileUri));
    evaluateNviCandidateHandler.handleRequest(evaluationEvent, EVALUATION_HANDLER_CONTEXT);

    if (!evaluationOutputQueue.getSentMessages().isEmpty()) {
      var upsertEvent = createUpsertEvent(getCandidateEvaluatedMessage());
      upsertNviCandidateHandler.handleRequest(upsertEvent, UPSERT_HANDLER_CONTEXT);
    }
  }

  public Instant getLastEvaluationTimestamp() {
    return lastEvaluationStartedAt;
  }

  private static SQSEvent createEvaluationEvent(PersistedResourceMessage persistedResourceMessage) {
    try {
      var body = objectMapper.writeValueAsString(persistedResourceMessage);
      return createEvent(body);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private SQSEvent createUpsertEvent(CandidateEvaluatedMessage candidateEvaluatedMessage) {
    try {
      var body = objectMapper.writeValueAsString(candidateEvaluatedMessage);
      return createEvent(body);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private CandidateEvaluatedMessage getCandidateEvaluatedMessage() {
    try {
      var sentMessages = evaluationOutputQueue.getSentMessages();
      var message = sentMessages.removeFirst();
      return objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static SQSEvent createEvent(String body) {
    var sqsEvent = new SQSEvent();
    var message = new SQSMessage();
    message.setBody(body);
    sqsEvent.setRecords(List.of(message));
    return sqsEvent;
  }
}
