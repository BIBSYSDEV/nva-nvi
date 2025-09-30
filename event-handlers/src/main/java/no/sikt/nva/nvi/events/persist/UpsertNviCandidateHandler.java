package no.sikt.nva.nvi.events.persist;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Objects;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.InvalidNviMessageException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpsertNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

  private static final String PUBLICATION_ID_NOT_FOUND = "publicationId not found";
  private static final Logger LOGGER = LoggerFactory.getLogger(UpsertNviCandidateHandler.class);
  private static final String INVALID_NVI_CANDIDATE_MESSAGE = "Invalid nvi candidate message";
  private static final String UPSERT_CANDIDATE_DLQ_QUEUE_URL = "UPSERT_CANDIDATE_DLQ_QUEUE_URL";
  private final CandidateRepository candidateRepository;
  private final QueueClient queueClient;
  private final String dlqUrl;

  @JacocoGenerated
  public UpsertNviCandidateHandler() {
    this(new CandidateRepository(defaultDynamoClient()), new NviQueueClient(), new Environment());
  }

  public UpsertNviCandidateHandler(
      CandidateRepository candidateRepository, QueueClient queueClient, Environment environment) {
    this.candidateRepository = candidateRepository;
    this.queueClient = queueClient;
    this.dlqUrl = environment.readEnv(UPSERT_CANDIDATE_DLQ_QUEUE_URL);
  }

  @Override
  public Void handleRequest(SQSEvent input, Context context) {
    LOGGER.info("Received event with {} records", input.getRecords().size());
    input.getRecords().stream()
        .map(SQSMessage::getBody)
        .map(this::parseBody)
        .filter(Objects::nonNull)
        .forEach(this::upsertNviCandidate);
    LOGGER.info("Finished processing all records");
    return null;
  }

  private static void validateMessage(CandidateEvaluatedMessage message) {
    attempt(
            () -> {
              Objects.requireNonNull(message.candidate().publicationId());
              return message;
            })
        .orElseThrow(failure -> new InvalidNviMessageException(INVALID_NVI_CANDIDATE_MESSAGE));
  }

  private static String extractPublicationId(CandidateEvaluatedMessage evaluatedCandidate) {
    var publicationId = Optional.ofNullable(evaluatedCandidate.candidate().publicationId());
    return isNull(publicationId) ? PUBLICATION_ID_NOT_FOUND : publicationId.toString();
  }

  private void upsertNviCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
    var publicationId = extractPublicationId(evaluatedCandidate);
    LOGGER.info("Processing publication: {}", publicationId);

    try {
      validateMessage(evaluatedCandidate);
      if (evaluatedCandidate.candidate() instanceof UpsertNviCandidateRequest candidate) {
        Candidate.upsert(candidate, candidateRepository);
      } else {
        var nonNviCandidate = (UpsertNonNviCandidateRequest) evaluatedCandidate.candidate();
        Candidate.updateNonCandidate(nonNviCandidate, candidateRepository);
      }
      LOGGER.info("NVI candidate persisted for publication: {}", publicationId);
    } catch (Exception e) {
      LOGGER.error("Failed to upsert candidate for publication: {}", publicationId);
      attempt(
          () ->
              queueClient.sendMessage(
                  UpsertDlqMessageBody.create(evaluatedCandidate, e).toJsonString(), dlqUrl));
    }
  }

  private CandidateEvaluatedMessage parseBody(String body) {
    return attempt(() -> dtoObjectMapper.readValue(body, CandidateEvaluatedMessage.class))
        .orElse(
            failure -> {
              logInvalidMessageBody(body);
              return null;
            });
  }

  private void logInvalidMessageBody(String body) {
    LOGGER.error("Message body invalid: {}", body);
  }
}
