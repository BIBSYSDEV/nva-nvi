package no.sikt.nva.nvi.events.batch;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.NviReceiveMessage;
import no.sikt.nva.nvi.common.queue.NviReceiveMessageResponse;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequeueDlqHandler implements RequestHandler<RequeueDlqInput, RequeueDlqOutput> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RequeueDlqHandler.class);
  private static final String DUPLICATE_MESSAGE_FOUND_IN_DLQ = "Duplicate message found in DLQ: %s";
  private static final String DLQ_QUEUE_URL_ENV_NAME = "DLQ_QUEUE_URL";
  private static final String HANDLER_FINISH_REPORT_LOG =
      "Requeue DLQ finished. In total {} messages processed. Success count: {}. "
          + "Failure count: {}. {} batches with errors.";
  private static final String REQUEUE_DLQ_STARTED_LOG =
      "Requeue DLQ started. {} messages to process.";
  private static final String DELETING_MESSAGE_FROM_DLQ_LOG = "Deleting message from DLQ: {}";
  private static final String PROCESSING_MESSAGE_LOG = "Processing message: {}";
  private static final String CANDIDATE_IDENTIFIER_ATTRIBUTE_NAME = "candidateIdentifier";
  private static final String COULD_NOT_UPDATE_CANDIDATE = "Could not update candidate: %s";
  private static final String COULD_NOT_PROCESS_MESSAGE_LOG =
      "Could not process message: %s. Missing message " + "attribute 'candidateIdentifier'";
  private static final int MAX_FAILURES = 5;
  private static final int MAX_SQS_MESSAGE_COUNT_LIMIT = 10;
  private final NviQueueClient queueClient;
  private final String queueUrl;
  private final CandidateRepository candidateRepository;

  @JacocoGenerated
  public RequeueDlqHandler() {
    this(
        new NviQueueClient(),
        new Environment().readEnv(DLQ_QUEUE_URL_ENV_NAME),
        new CandidateRepository(defaultDynamoClient()));
  }

  public RequeueDlqHandler(
      NviQueueClient queueClient, String dlqQueueUrl, CandidateRepository candidateRepository) {
    this.queueClient = queueClient;
    this.queueUrl = dlqQueueUrl;
    this.candidateRepository = candidateRepository;
  }

  @Override
  public RequeueDlqOutput handleRequest(RequeueDlqInput input, Context context) {
    LOGGER.info(REQUEUE_DLQ_STARTED_LOG, input.count());

    var messageIds = new HashSet<String>();

    var remainingMessages = input.count();
    var failedBatchesCount = 0;
    var result = new HashSet<NviProcessMessageResult>();

    while (remainingMessages > 0) {
      var messagesToReceive = Math.min(remainingMessages, MAX_SQS_MESSAGE_COUNT_LIMIT);
      var response = queueClient.receiveMessage(queueUrl, messagesToReceive);

      if (shouldBreakLoop(response, failedBatchesCount)) {
        break;
      }

      var processedMessages = processMessages(response, messageIds);

      result.addAll(processedMessages);
      failedBatchesCount += checkForFailedBatch(processedMessages);
      remainingMessages -= messagesToReceive;
    }

    LOGGER.info(
        HANDLER_FINISH_REPORT_LOG,
        result.size(),
        result.stream().filter(NviProcessMessageResult::success).count(),
        result.stream().filter(a -> !a.success()).count(),
        failedBatchesCount);

    return new RequeueDlqOutput(result, failedBatchesCount);
  }

  private static NviProcessMessageResult checkForDuplicates(
      Set<String> messageIds, NviReceiveMessage message) {
    var isUnique = messageIds.add(message.messageId());
    if (!isUnique) {
      var warning = String.format(DUPLICATE_MESSAGE_FOUND_IN_DLQ, message.messageId());
      LOGGER.warn(warning);
      return new NviProcessMessageResult(message, false, Optional.of(warning));
    }
    return new NviProcessMessageResult(message, true, Optional.empty());
  }

  private boolean shouldBreakLoop(NviReceiveMessageResponse response, int failedBatchesCount) {
    return response.messages().isEmpty() || failedBatchesCount >= MAX_FAILURES;
  }

  private Set<NviProcessMessageResult> processMessages(
      NviReceiveMessageResponse response, Set<String> messageIds) {
    return response.messages().stream()
        .map(message -> checkForDuplicates(messageIds, message))
        .map(this::processMessage)
        .map(this::deleteMessageFromDlq)
        .collect(Collectors.toSet());
  }

  private int checkForFailedBatch(Set<NviProcessMessageResult> processedMessages) {
    return processedMessages.stream().anyMatch(a -> !a.success()) ? 1 : 0;
  }

  private NviProcessMessageResult deleteMessageFromDlq(NviProcessMessageResult message) {
    LOGGER.info(DELETING_MESSAGE_FROM_DLQ_LOG, message.message().body());
    if (message.success()) {
      queueClient.deleteMessage(queueUrl, message.message().receiptHandle());
    }
    return message;
  }

  private NviProcessMessageResult processMessage(NviProcessMessageResult input) {
    LOGGER.info(PROCESSING_MESSAGE_LOG, input.message().body());

    if (!input.success()) {
      return input;
    }

    var identifier = input.message().messageAttributes().get(CANDIDATE_IDENTIFIER_ATTRIBUTE_NAME);
    if (nonNull(identifier)) {
      try {
        var candidateIdentifier = UUID.fromString(identifier);
        var originalCandidate =
            candidateRepository
                .findCandidateById(candidateIdentifier)
                .orElseThrow(CandidateNotFoundException::new);
        candidateRepository.updateCandidate(originalCandidate);
      } catch (Exception exception) {
        return new NviProcessMessageResult(
            input.message(),
            false,
            Optional.of(String.format(COULD_NOT_UPDATE_CANDIDATE, identifier)));
      }
    } else {
      var message = String.format(COULD_NOT_PROCESS_MESSAGE_LOG, input.message().body());
      LOGGER.error(message);
      return new NviProcessMessageResult(input.message(), false, Optional.of(message));
    }
    return new NviProcessMessageResult(input.message(), true, Optional.empty());
  }
}
