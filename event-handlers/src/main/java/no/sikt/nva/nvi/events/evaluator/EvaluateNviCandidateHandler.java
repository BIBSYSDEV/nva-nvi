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
import no.sikt.nva.nvi.common.model.events.PersistedResourceMessage;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluateNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(EvaluateNviCandidateHandler.class);
  private final CandidateService candidateService;
  private final EvaluatorService evaluatorService;

  @JacocoGenerated
  public EvaluateNviCandidateHandler() {
    this(CandidateService.defaultCandidateService(), EvaluatorService.defaultEvaluatorService());
  }

  public EvaluateNviCandidateHandler(
      CandidateService candidateService, EvaluatorService evaluatorService) {
    this.candidateService = candidateService;
    this.evaluatorService = evaluatorService;
  }

  @Override
  public Void handleRequest(SQSEvent event, Context context) {
    LOGGER.info("Processing event with {} messages", event.getRecords().size());
    evaluateCandidacy(extractPersistedResourceMessage(event)).ifPresent(this::persistResult);
    LOGGER.info("Event processed successfully");
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
