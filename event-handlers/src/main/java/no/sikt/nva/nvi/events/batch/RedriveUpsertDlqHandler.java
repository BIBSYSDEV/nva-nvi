package no.sikt.nva.nvi.events.batch;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.net.URI;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.dto.CandidateType;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.NviReceiveMessage;
import no.sikt.nva.nvi.common.queue.NviReceiveMessageResponse;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.events.persist.UpsertDlqMessageBody;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedriveUpsertDlqHandler implements RequestHandler<RedriveUpsertDlqInput, Void> {

  public static final String RESOURCES = "resources";
  public static final String COULD_NOT_EXTRACT_PUBLICATION_ID_FROM_MESSAGE =
      "Could not extract publicationId from " + "message: ";
  public static final String DUPLICATE_MESSAGE_FOUND =
      "Duplicate message found for URI: {}, keeping first " + "occurrence";
  private static final int DEFAULT_COUNT = 10;
  private static final Logger LOGGER = LoggerFactory.getLogger(RedriveUpsertDlqHandler.class);
  private static final String UPSERT_CANDIDATE_DLQ_QUEUE_URL = "UPSERT_CANDIDATE_DLQ_QUEUE_URL";
  private static final String RESOURCE_EVALUATION_QUEUE_URL = "PERSISTED_RESOURCE_QUEUE_URL";
  private static final String HANDLER_FINISH_REPORT_LOG =
      "Redrive UpsertCandidateDLQ finished. In total {} " + "messages processed";
  private static final String REDRIVE_DLQ_STARTED_LOG =
      "Redrive UpsertCandidateDLQ started. {} messages to process.";
  private static final String REPUBLISHING_TO_EVALUATION_QUEUE_LOG =
      "Republishing to ResourceEvaluationQueue: {}";
  private static final int MAX_FAILURES = 5;
  private static final int MAX_SQS_MESSAGE_COUNT_LIMIT = 10;
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private final NviQueueClient queueClient;
  private final String dlqQueueUrl;
  private final String evaluationQueueUrl;
  private final String expandedResourcesBucket;

  @JacocoGenerated
  public RedriveUpsertDlqHandler() {
    this(new NviQueueClient(), new Environment());
  }

  public RedriveUpsertDlqHandler(NviQueueClient queueClient, Environment environment) {
    this.queueClient = queueClient;
    this.dlqQueueUrl = environment.readEnv(UPSERT_CANDIDATE_DLQ_QUEUE_URL);
    this.evaluationQueueUrl = environment.readEnv(RESOURCE_EVALUATION_QUEUE_URL);
    this.expandedResourcesBucket = environment.readEnv(EXPANDED_RESOURCES_BUCKET);
  }

  @Override
  public Void handleRequest(RedriveUpsertDlqInput input, Context context) {
    var messageCount = getMessageCount(input);

    LOGGER.info(REDRIVE_DLQ_STARTED_LOG, messageCount);

    var failedBatchesCount = 0;
    Set<String> result = new HashSet<>();

    while (messageCount > 0) {
      var messagesToReceive = Math.min(messageCount, MAX_SQS_MESSAGE_COUNT_LIMIT);
      var response = queueClient.receiveMessage(dlqQueueUrl, messagesToReceive);

      if (shouldBreakLoop(response, failedBatchesCount)) {
        break;
      }

      var processedMessages = processMessages(response);
      processedMessages.forEach(
          receiptHandle -> queueClient.deleteMessage(dlqQueueUrl, receiptHandle));
      result.addAll(processedMessages);
      messageCount -= processedMessages.size();
    }

    LOGGER.info(HANDLER_FINISH_REPORT_LOG, result.size());

    return null;
  }

  private static Integer getMessageCount(RedriveUpsertDlqInput input) {
    return Optional.ofNullable(input).map(RedriveUpsertDlqInput::count).orElse(DEFAULT_COUNT);
  }

  private boolean shouldBreakLoop(NviReceiveMessageResponse response, int failedBatchesCount) {
    return response.messages().isEmpty() || failedBatchesCount >= MAX_FAILURES;
  }

  private Set<String> processMessages(NviReceiveMessageResponse response) {

    var uniqueMessages =
        response.messages().stream()
            .collect(
                Collectors.toMap(
                    this::extractS3UriFromMessage, Function.identity(), this::handleDuplicate));

    return uniqueMessages.entrySet().stream()
        .map(entry -> processEntry(entry.getKey(), entry.getValue().receiptHandle()))
        .collect(Collectors.toSet());
  }

  private NviReceiveMessage handleDuplicate(
      NviReceiveMessage existing, NviReceiveMessage duplicate) {
    LOGGER.warn(DUPLICATE_MESSAGE_FOUND, extractS3UriFromMessage(existing));
    queueClient.deleteMessage(dlqQueueUrl, duplicate.receiptHandle());
    return existing;
  }

  private String processEntry(URI uri, String receiptHandle) {
    var persistedResourceMessage = new PersistedResourceMessage(uri);
    var messageToRepublish =
        attempt(() -> dtoObjectMapper.writeValueAsString(persistedResourceMessage)).orElseThrow();
    LOGGER.info(REPUBLISHING_TO_EVALUATION_QUEUE_LOG, messageToRepublish);
    queueClient.sendMessage(evaluationQueueUrl, messageToRepublish);
    return receiptHandle;
  }

  private URI extractS3UriFromMessage(NviReceiveMessage message) {
    return UpsertDlqMessageBody.fromString(message.body())
        .map(UpsertDlqMessageBody::evaluatedMessage)
        .map(CandidateEvaluatedMessage::candidate)
        .map(this::extractS3UriFromMessage)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    COULD_NOT_EXTRACT_PUBLICATION_ID_FROM_MESSAGE + message.body()));
  }

  private URI extractS3UriFromMessage(CandidateType candidateType) {
    if (candidateType instanceof UpsertNviCandidateRequest upsertNviCandidateRequest) {
      return upsertNviCandidateRequest.publicationBucketUri();
    } else {
      var publicationId = candidateType.publicationId();
      var publicationIdentifier = UriWrapper.fromUri(publicationId).getLastPathElement();
      return URI.create(
          String.format(
              "s3://%s/%s/%s.gz", expandedResourcesBucket, RESOURCES, publicationIdentifier));
    }
  }
}
