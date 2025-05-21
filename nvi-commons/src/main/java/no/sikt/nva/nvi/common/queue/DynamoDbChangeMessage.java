package no.sikt.nva.nvi.common.queue;

import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.UUID;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

/**
 * Simplified representation of the DynamoDB event message sent when a record is updated in the
 * database. This is used for records that either are or are directly related to a Candidate. This
 * is used to avoid sending the entire original DynamoDB event message, which can be too big to fit
 * on a queue.
 *
 * @param candidateIdentifier the identifier of the related candidate
 * @param entryType the type of entry being changed, which is not necessarily the same as the type
 *     of the DAO
 * @param operationType the type of DynamoDB operation that triggered the event
 */
public record DynamoDbChangeMessage(
    UUID candidateIdentifier, DataEntryType entryType, OperationType operationType) {

  public static DynamoDbChangeMessage from(String json) throws JsonProcessingException {
    var dbChangeMessage = dtoObjectMapper.readValue(json, DynamoDbChangeMessage.class);
    dbChangeMessage.validate();
    return dbChangeMessage;
  }

  public String toJsonString() throws JsonProcessingException {
    return dtoObjectMapper.writeValueAsString(this);
  }

  public void validate() {
    shouldNotBeNull(candidateIdentifier, "candidateIdentifier must not be null");
    shouldNotBeNull(entryType, "entryType must not be null");
    shouldNotBeNull(operationType, "operationType must not be null");
  }
}
