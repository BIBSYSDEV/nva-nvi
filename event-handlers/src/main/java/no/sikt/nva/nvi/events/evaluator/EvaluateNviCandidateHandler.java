package no.sikt.nva.nvi.events.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Optional;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.queue.QueueMessage;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluateNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(EvaluateNviCandidateHandler.class);
  private static final String EVALUATED_CANDIDATE_QUEUE_URL = "CANDIDATE_QUEUE_URL";
  private static final String EVALUATION_DLQ_URL = "EVALUATION_DLQ_URL";
  private static final String BACKEND_CLIENT_AUTH_URL = "BACKEND_CLIENT_AUTH_URL";
  private static final String BACKEND_CLIENT_SECRET_NAME = "BACKEND_CLIENT_SECRET_NAME";
  private final EvaluatorService evaluatorService;
  private final QueueClient queueClient;
  private final String queueUrl;
  private final String evaluationDlqUrl;

  @JacocoGenerated
  public EvaluateNviCandidateHandler() {
    this(
        new EvaluatorService(
            new S3StorageReader(new Environment().readEnv("EXPANDED_RESOURCES_BUCKET")),
            new CreatorVerificationUtil(
                authorizedUriRetriever(new Environment()), new Environment()),
            CandidateService.defaultCandidateService()),
        new NviQueueClient(),
        new Environment());
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

  @JacocoGenerated
  private static AuthorizedBackendUriRetriever authorizedUriRetriever(Environment env) {
    return new AuthorizedBackendUriRetriever(
        env.readEnv(BACKEND_CLIENT_AUTH_URL), env.readEnv(BACKEND_CLIENT_SECRET_NAME));
  }

  private Void handleFailure(SQSEvent input, Failure<?> failure) {
    LOGGER.error("Failed to process event: {}", input.toString(), failure.getException());
    var message =
        QueueMessage.builder()
            .withBody(extractPersistedResourceMessage(input))
            .withErrorContext(failure.getException())
            .build();
    queueClient.sendMessage(message, evaluationDlqUrl);
    return null;
  }

  private static PersistedResourceMessage parseBody(String body) {
    return attempt(() -> dtoObjectMapper.readValue(body, PersistedResourceMessage.class))
        .orElseThrow();
  }

  private void sendEvent(CandidateEvaluatedMessage candidateEvaluatedMessage) {
    logEvaluationOutcome(candidateEvaluatedMessage);
    LOGGER.info(
        "Sending evaluated publication {} to upsert queue",
        candidateEvaluatedMessage.publicationId());
    var message =
        QueueMessage.builder()
            .withBody(candidateEvaluatedMessage)
            .withPublicationId(candidateEvaluatedMessage.publicationId())
            .build();
    queueClient.sendMessage(message, queueUrl);
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
