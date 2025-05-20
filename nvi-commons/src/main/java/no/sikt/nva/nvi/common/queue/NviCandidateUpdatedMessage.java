package no.sikt.nva.nvi.common.queue;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.UUID;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public record NviCandidateUpdatedMessage(
    UUID candidateIdentifier, DataEntryType entryType, OperationType operationType) {

  public static NviCandidateUpdatedMessage from(String json) throws JsonProcessingException {
    return dtoObjectMapper.readValue(json, NviCandidateUpdatedMessage.class);
  }

  public static String toJsonString(NviCandidateUpdatedMessage message)
      throws JsonProcessingException {
    return dtoObjectMapper.writeValueAsString(message);
  }
}
