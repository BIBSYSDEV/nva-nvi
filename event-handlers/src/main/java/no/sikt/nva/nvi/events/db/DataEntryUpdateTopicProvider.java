package no.sikt.nva.nvi.events.db;

import no.sikt.nva.nvi.common.queue.DataEntryType;
import no.sikt.nva.nvi.common.queue.NviCandidateUpdatedMessage;
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

  public String getTopic(NviCandidateUpdatedMessage message) {
    return switch (message.operationType()) {
      case INSERT -> getInsertTopic(message.entryType());
      case MODIFY -> getUpdateTopic(message.entryType());
      case REMOVE -> getRemoveTopic(message.entryType());
      default ->
          throw new IllegalArgumentException(ILLEGAL_ARGUMENT_MESSAGE + message.operationType());
    };
  }

  private String getInsertTopic(DataEntryType entryType) {
    switch (entryType) {
      case DataEntryType.CANDIDATE -> {
        return environment.readEnv(CANDIDATE_INSERT_TOPIC);
      }
      case DataEntryType.APPROVAL_STATUS -> {
        return environment.readEnv(APPROVAL_INSERT_TOPIC);
      }
      default -> throw new IllegalArgumentException(ILLEGAL_ARGUMENT_MESSAGE + entryType);
    }
  }

  private String getUpdateTopic(DataEntryType entryType) {
    switch (entryType) {
      case DataEntryType.CANDIDATE -> {
        return environment.readEnv(CANDIDATE_APPLICABLE_UPDATE_TOPIC);
      }
      case DataEntryType.NON_CANDIDATE -> {
        return environment.readEnv(CANDIDATE_NOT_APPLICABLE_UPDATE_TOPIC);
      }
      case DataEntryType.APPROVAL_STATUS -> {
        return environment.readEnv(APPROVAL_UPDATE_TOPIC);
      }
      default -> throw new IllegalArgumentException(ILLEGAL_ARGUMENT_MESSAGE + entryType);
    }
  }

  private String getRemoveTopic(DataEntryType entryType) {
    switch (entryType) {
      case DataEntryType.CANDIDATE, DataEntryType.NON_CANDIDATE -> {
        return environment.readEnv(CANDIDATE_REMOVE_TOPIC);
      }
      case DataEntryType.APPROVAL_STATUS -> {
        return environment.readEnv(APPROVAL_REMOVE_TOPIC);
      }
      default -> throw new IllegalArgumentException(ILLEGAL_ARGUMENT_MESSAGE + entryType);
    }
  }
}
