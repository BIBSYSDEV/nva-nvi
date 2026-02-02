package no.sikt.nva.nvi.common.queue;

import static java.util.Objects.requireNonNull;
import static nva.commons.core.StringUtils.isNotBlank;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public final class QueueMessageAttributes {

  public static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
  public static final String PUBLICATION_ID = "publicationId";
  public static final String PUBLICATION_BUCKET_URI = "publicationBucketUri";
  public static final String ERROR_MESSAGE = "errorMessage";
  public static final String ERROR_TYPE = "errorType";
  public static final String FAILED_AT = "failedAt";
  public static final String STACK_TRACE = "stackTrace";
  private static final String DATA_TYPE_STRING = "String";
  private static final int MAX_STACK_TRACE_LENGTH = 200;

  private final Map<String, String> attributes = new HashMap<>();

  private QueueMessageAttributes() {}

  public static QueueMessageAttributes builder() {
    return new QueueMessageAttributes();
  }

  public static QueueMessageAttributes fromCandidateIdentifier(UUID candidateIdentifier) {
    requireNonNull(candidateIdentifier);
    return builder().withAttribute(CANDIDATE_IDENTIFIER, candidateIdentifier.toString());
  }

  public static QueueMessageAttributes fromPublicationId(URI publicationId) {
    requireNonNull(publicationId);
    return builder().withAttribute(PUBLICATION_ID, publicationId.toString());
  }

  public static QueueMessageAttributes fromPublicationBucketUri(URI publicationBucketUri) {
    requireNonNull(publicationBucketUri);
    return builder().withAttribute(PUBLICATION_BUCKET_URI, publicationBucketUri.toString());
  }

  public QueueMessageAttributes withErrorContext(Exception cause) {
    attributes.put(ERROR_MESSAGE, cause.getMessage());
    attributes.put(ERROR_TYPE, cause.getClass().getSimpleName());
    attributes.put(FAILED_AT, Instant.now().toString());
    attributes.put(STACK_TRACE, truncatedStackTrace(cause));
    return this;
  }

  public QueueMessageAttributes withAttribute(String key, String value) {
    if (isNotBlank(key) && isNotBlank(value)) {
      attributes.put(key, value);
    }
    return this;
  }

  public Map<String, MessageAttributeValue> build() {
    return toMessageAttributeValues(attributes);
  }

  private static String truncatedStackTrace(Exception cause) {
    var stringWriter = new StringWriter();
    cause.printStackTrace(new PrintWriter(stringWriter));
    var fullStackTrace = stringWriter.toString();
    if (fullStackTrace.length() <= MAX_STACK_TRACE_LENGTH) {
      return fullStackTrace;
    }
    return fullStackTrace.substring(0, MAX_STACK_TRACE_LENGTH) + "...";
  }

  private static Map<String, MessageAttributeValue> toMessageAttributeValues(
      Map<String, String> attributes) {
    return attributes.entrySet().stream()
        .collect(
            Collectors.toMap(Entry::getKey, entry -> toMessageAttributeValue(entry.getValue())));
  }

  private static MessageAttributeValue toMessageAttributeValue(String entry) {
    return MessageAttributeValue.builder().stringValue(entry).dataType(DATA_TYPE_STRING).build();
  }
}
