package cucumber.contexts;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getEvaluateNviCandidateHandlerEnvironment;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.unit.nva.clients.CustomerDto;
import no.unit.nva.clients.CustomerList;
import no.unit.nva.clients.IdentityServiceClient;
import nva.commons.apigateway.exceptions.ApiGatewayException;

public class EvaluationContext {
  private static final Context EVALUATION_HANDLER_CONTEXT = mock(Context.class);

  private final EvaluateNviCandidateHandler evaluateNviCandidateHandler;
  private final IdentityServiceClient identityServiceClient;
  private final FakeSqsClient evaluationOutputQueue;
  private final TestScenario scenario;
  private Instant lastEvaluationStartedAt;

  public EvaluationContext(TestScenario scenario) {
    this.scenario = scenario;
    identityServiceClient = mock(IdentityServiceClient.class);
    evaluationOutputQueue = new FakeSqsClient();
    evaluateNviCandidateHandler = createEvaluateNviCandidateHandler();
  }

  private EvaluateNviCandidateHandler createEvaluateNviCandidateHandler() {
    var environment = getEvaluateNviCandidateHandlerEnvironment();
    var candidateService = scenario.getCandidateService();
    var evaluatorService =
        new EvaluatorService(
            identityServiceClient,
            scenario.getS3StorageReaderForExpandedResourcesBucket(),
            candidateService);

    return new EvaluateNviCandidateHandler(
        candidateService, evaluatorService, evaluationOutputQueue, environment);
  }

  public void mockGetAllCustomersResponse(List<CustomerDto> customers) {
    try {
      when(identityServiceClient.getAllCustomers()).thenReturn(new CustomerList(customers));
    } catch (ApiGatewayException exception) {
      throw new RuntimeException(exception);
    }
  }

  public void evaluatePublicationAndPersistResult(SampleExpandedPublication publication) {
    evaluatePublicationAndPersistResult(publication.toJsonString());
  }

  public void evaluatePublicationAndPersistResult(String publicationJson) {
    lastEvaluationStartedAt = Instant.now();
    var fileUri = scenario.setupExpandedPublicationInS3(publicationJson);
    var evaluationEvent = createEvaluationEvent(new PersistedResourceMessage(fileUri));
    evaluateNviCandidateHandler.handleRequest(evaluationEvent, EVALUATION_HANDLER_CONTEXT);
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

  private static SQSEvent createEvent(String body) {
    var sqsEvent = new SQSEvent();
    var message = new SQSMessage();
    message.setBody(body);
    sqsEvent.setRecords(List.of(message));
    return sqsEvent;
  }
}
