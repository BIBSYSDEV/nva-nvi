package no.sikt.nva.nvi.events.db;

import no.sikt.nva.nvi.common.queue.DataEntryType;
import no.sikt.nva.nvi.common.queue.DynamoDbChangeMessage;
import nva.commons.core.Environment;

public class DataEntryUpdateTopicProvider {

  private static final String CANDIDATE_REMOVE_TOPIC = "TOPIC_CANDIDATE_REMOVE";
  private static final String APPROVAL_REMOVE_TOPIC = "TOPIC_APPROVAL_REMOVE";
  private static final String CANDIDATE_INSERT_TOPIC = "TOPIC_CANDIDATE_INSERT";
  private static final String APPROVAL_INSERT_TOPIC = "TOPIC_APPROVAL_INSERT";
  private static final String APPROVAL_UPDATE_TOPIC = "TOPIC_APPROVAL_UPDATE";
  private static final String CANDIDATE_APPLICABLE_UPDATE_TOPIC =
      "TOPIC_CANDIDATE_APPLICABLE_UPDATE";
  private static final String CANDIDATE_NOT_APPLICABLE_UPDATE_TOPIC =
      "TOPIC_CANDIDATE_NOT_APPLICABLE_UPDATE";
  private static final String ILLEGAL_ARGUMENT_MESSAGE = "Illegal entry type: ";
  private final Environment environment;

  public DataEntryUpdateTopicProvider(Environment environment) {
    this.environment = environment;
  }

  public String getTopic(DynamoDbChangeMessage message) {
    return switch (message.operationType()) {
      case INSERT -> getInsertTopic(message.entryType());
      case MODIFY -> getUpdateTopic(message.entryType());
      case REMOVE -> getRemoveTopic(message.entryType());
      default ->
          throw new IllegalArgumentException(ILLEGAL_ARGUMENT_MESSAGE + message.operationType());
    };
  }

  private String getInsertTopic(DataEntryType entryType) {
    return switch (entryType) {
      case CANDIDATE -> environment.readEnv(CANDIDATE_INSERT_TOPIC);
      case APPROVAL_STATUS -> environment.readEnv(APPROVAL_INSERT_TOPIC);
      default -> throw new IllegalArgumentException(ILLEGAL_ARGUMENT_MESSAGE + entryType);
    };
  }

  private String getUpdateTopic(DataEntryType entryType) {
    return switch (entryType) {
      case CANDIDATE -> environment.readEnv(CANDIDATE_APPLICABLE_UPDATE_TOPIC);
      case NON_CANDIDATE -> environment.readEnv(CANDIDATE_NOT_APPLICABLE_UPDATE_TOPIC);
      case APPROVAL_STATUS -> environment.readEnv(APPROVAL_UPDATE_TOPIC);
      default -> throw new IllegalArgumentException(ILLEGAL_ARGUMENT_MESSAGE + entryType);
    };
  }

  private String getRemoveTopic(DataEntryType entryType) {
    return switch (entryType) {
      case CANDIDATE, DataEntryType.NON_CANDIDATE -> environment.readEnv(CANDIDATE_REMOVE_TOPIC);
      case APPROVAL_STATUS -> environment.readEnv(APPROVAL_REMOVE_TOPIC);
      default -> throw new IllegalArgumentException(ILLEGAL_ARGUMENT_MESSAGE + entryType);
    };
  }
}
