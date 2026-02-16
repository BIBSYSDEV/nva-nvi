package no.sikt.nva.nvi.events.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Optional;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.queue.QueueMessage;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluateNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(EvaluateNviCandidateHandler.class);
  private static final String EVALUATION_DLQ_URL = "EVALUATION_DLQ_URL";
  private final CandidateService candidateService;
  private final EvaluatorService evaluatorService;
  private final QueueClient queueClient;
  private final String evaluationDlqUrl;

  @JacocoGenerated
  public EvaluateNviCandidateHandler() {
    this(
        CandidateService.defaultCandidateService(),
        EvaluatorService.defaultEvaluatorService(),
        new NviQueueClient(),
        new Environment());
  }

  public EvaluateNviCandidateHandler(
      CandidateService candidateService,
      EvaluatorService evaluatorService,
      QueueClient queueClient,
      Environment environment) {
    this.candidateService = candidateService;
    this.evaluatorService = evaluatorService;
    this.queueClient = queueClient;
    this.evaluationDlqUrl = environment.readEnv(EVALUATION_DLQ_URL);
  }

  @Override
  public Void handleRequest(SQSEvent input, Context context) {
    attempt(
            () -> {
              evaluateCandidacy(extractPersistedResourceMessage(input))
                  .ifPresent(this::persistResult);
              return null;
            })
        .orElse(failure -> handleFailure(input, failure));
    return null;
  }

  private void persistResult(CandidateEvaluatedMessage candidateEvaluatedMessage) {
    LOGGER.info(
        "Upserting evaluation result for publication {}",
        candidateEvaluatedMessage.publicationId());
    var candidate = candidateEvaluatedMessage.candidate();
    switch (candidate) {
      case UpsertNviCandidateRequest candidateRequest ->
          candidateService.upsertCandidate(candidateRequest);
      case UpsertNonNviCandidateRequest nonNviCandidateRequest ->
          candidateService.updateCandidate(nonNviCandidateRequest);
    }
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
