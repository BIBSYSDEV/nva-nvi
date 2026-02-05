package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluateNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(EvaluateNviCandidateHandler.class);
  private static final String EVALUATED_CANDIDATE_QUEUE_URL = "CANDIDATE_QUEUE_URL";
  private static final String EVALUATION_DLQ_URL = "EVALUATION_DLQ_URL";
  private static final String FAILURE_MESSAGE =
      "Failure while calculating NVI Candidate: %s, exception: %s, message: %s";
  private static final String EXCEPTION_FIELD = "exception";
  private final EvaluatorService evaluatorService;
  private final QueueClient queueClient;
  private final String queueUrl;
  private final String evaluationDlqUrl;

  @JacocoGenerated
  public EvaluateNviCandidateHandler() {
    this(EvaluatorService.defaultEvaluatorService(), new NviQueueClient(), new Environment());
  }

  public EvaluateNviCandidateHandler(
      EvaluatorService evaluatorService, QueueClient queueClient, Environment environment) {
    this.evaluatorService = evaluatorService;
    this.queueClient = queueClient;
    this.queueUrl = environment.readEnv(EVALUATED_CANDIDATE_QUEUE_URL);
    this.evaluationDlqUrl = environment.readEnv(EVALUATION_DLQ_URL);
  }

  @Override
  public Void handleRequest(SQSEvent input, Context context) {
    attempt(
            () -> {
              evaluateCandidacy(extractPersistedResourceMessage(input)).ifPresent(this::sendEvent);
              return null;
            })
        .orElse(failure -> handleFailure(input, failure));
    return null;
  }

  private Void handleFailure(SQSEvent input, Failure<?> failure) {
    LOGGER.error(
        String.format(
            FAILURE_MESSAGE,
            input.toString(),
            failure.getException(),
            failure.getException().getMessage()));
    var messageBody = extractPersistedResourceMessage(input);
    var dlqMessageBody =
        injectExceptionIntoJson(messageBody.toJsonString(), failure.getException());
    queueClient.sendMessage(dlqMessageBody.orElse(messageBody.toJsonString()), evaluationDlqUrl);
    return null;
  }

  private Optional<String> injectExceptionIntoJson(String jsonMessage, Exception exception) {
    return attempt(() -> dtoObjectMapper.readTree(jsonMessage))
        .map(ObjectNode.class::cast)
        .map(tree -> tree.put(EXCEPTION_FIELD, getStackTrace(exception)))
        .map(ObjectNode::toString)
        .toOptional();
  }

  private static PersistedResourceMessage parseBody(String body) {
    return attempt(() -> dtoObjectMapper.readValue(body, PersistedResourceMessage.class))
        .orElseThrow();
  }

  private void sendEvent(CandidateEvaluatedMessage candidateEvaluatedMessage) {
    logEvaluationOutcome(candidateEvaluatedMessage);
    var messageBody =
        attempt(() -> dtoObjectMapper.writeValueAsString(candidateEvaluatedMessage)).orElseThrow();

    LOGGER.info(
        "Sending evaluated publication {} to upsert queue",
        candidateEvaluatedMessage.publicationId());
    queueClient.sendMessage(messageBody, queueUrl);
  }

  private static void logEvaluationOutcome(CandidateEvaluatedMessage candidateEvaluatedMessage) {
    if (candidateEvaluatedMessage.candidate() instanceof UpsertNviCandidateRequest) {
      LOGGER.info("Received UpsertNviCandidateRequest");
    } else {
      LOGGER.info("Received NonNviCandidateRequest");
    }
  }

  private PersistedResourceMessage extractPersistedResourceMessage(SQSEvent input) {
    return attempt(() -> parseBody(extractFirstMessage(input).getBody())).orElseThrow();
  }

  private SQSMessage extractFirstMessage(SQSEvent input) {
    return input.getRecords().stream().findFirst().orElseThrow();
  }

  private Optional<CandidateEvaluatedMessage> evaluateCandidacy(PersistedResourceMessage message) {
    return evaluatorService.evaluateCandidacy(message.resourceFileUri());
  }
}
