package no.sikt.nva.nvi.common.queue;

import static java.util.Objects.nonNull;
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
import no.unit.nva.commons.json.JsonSerializable;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public record QueueMessage(JsonSerializable body, Map<String, MessageAttributeValue> attributes) {

  private static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
  private static final String PUBLICATION_ID = "publicationId";
  private static final String PUBLICATION_BUCKET_URI = "publicationBucketUri";
  private static final String ERROR_MESSAGE = "errorMessage";
  private static final String ERROR_TYPE = "errorType";
  private static final String FAILED_AT = "failedAt";
  private static final String STACK_TRACE = "stackTrace";
  private static final String DATA_TYPE_STRING = "String";
  private static final int MAX_STACK_TRACE_LENGTH = 1000;

  public static Builder builder() {
    return new Builder();
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

  private static MessageAttributeValue toMessageAttributeValue(String value) {
    return MessageAttributeValue.builder().stringValue(value).dataType(DATA_TYPE_STRING).build();
  }

  public static class Builder {

    private final Map<String, String> attributes = new HashMap<>();
    private JsonSerializable body;

    public Builder withBody(JsonSerializable body) {
      this.body = body;
      return this;
    }

    public Builder withCandidateIdentifier(UUID candidateIdentifier) {
      return withAttribute(CANDIDATE_IDENTIFIER, candidateIdentifier.toString());
    }

    public Builder withPublicationId(URI publicationId) {
      return withAttribute(PUBLICATION_ID, publicationId.toString());
    }

    public Builder withPublicationBucketUri(URI publicationBucketUri) {
      return withAttribute(PUBLICATION_BUCKET_URI, publicationBucketUri.toString());
    }

    public Builder withErrorContext(Exception cause) {
      putIfNotNull(ERROR_MESSAGE, cause.getMessage());
      attributes.put(ERROR_TYPE, cause.getClass().getSimpleName());
      attributes.put(FAILED_AT, Instant.now().toString());
      attributes.put(STACK_TRACE, truncatedStackTrace(cause));
      return this;
    }

    public Builder withAttribute(String key, String value) {
      putIfNotBlank(key, value);
      return this;
    }

    public QueueMessage build() {
      requireNonNull(body, "Message body is required");
      return new QueueMessage(body, toMessageAttributeValues(attributes));
    }

    private void putIfNotNull(String key, Object value) {
      if (nonNull(value)) {
        attributes.put(key, value.toString());
      }
    }

    private void putIfNotBlank(String key, String value) {
      if (isNotBlank(key) && isNotBlank(value)) {
        attributes.put(key, value);
      }
    }
  }
}
