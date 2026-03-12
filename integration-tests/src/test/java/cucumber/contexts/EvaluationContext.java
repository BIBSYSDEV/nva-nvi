package cucumber.contexts;

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
import no.sikt.nva.nvi.common.service.exception.IllegalCandidateUpdateException;
import no.sikt.nva.nvi.events.evaluator.EvaluateNviCandidateHandler;
import no.sikt.nva.nvi.events.evaluator.EvaluatorService;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.unit.nva.clients.CustomerDto;
import no.unit.nva.clients.CustomerList;
import no.unit.nva.clients.IdentityServiceClient;
import no.unit.nva.stubs.FakeContext;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluationContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(EvaluationContext.class);
  private static final Context EVALUATION_HANDLER_CONTEXT = new FakeContext();

  private final EvaluateNviCandidateHandler evaluateNviCandidateHandler;
  private final IdentityServiceClient identityServiceClient;
  private final TestScenario scenario;
  private Instant lastEvaluationStartedAt;

  public EvaluationContext(TestScenario scenario) {
    this.scenario = scenario;
    identityServiceClient = mock(IdentityServiceClient.class);
    evaluateNviCandidateHandler = createEvaluateNviCandidateHandler();
  }

  private EvaluateNviCandidateHandler createEvaluateNviCandidateHandler() {
    var candidateService = scenario.getCandidateService();
    var evaluatorService =
        new EvaluatorService(
            identityServiceClient,
            scenario.getS3StorageReaderForExpandedResourcesBucket(),
            candidateService);

    return new EvaluateNviCandidateHandler(candidateService, evaluatorService);
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

  /**
   * FIXME: Ignoring failures because validation rules are inconsistent. Cannot be fixed until
   * reporting period lifecycle is implemented (NP-49541).
   */
  public void evaluatePublicationIgnoringFailure(SampleExpandedPublication publication) {
    try {
      evaluatePublicationAndPersistResult(publication);
    } catch (IllegalCandidateUpdateException exception) {
      LOGGER.info("Evaluation failed: {}", exception.getMessage());
    }
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
