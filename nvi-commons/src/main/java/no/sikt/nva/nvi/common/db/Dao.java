package no.sikt.nva.nvi.common.db;

import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@JsonSerialize
@JsonSubTypes({
    @JsonSubTypes.Type(value = ApprovalStatusDao.class, name = ApprovalStatusDao.TYPE),
    @JsonSubTypes.Type(value = CandidateDao.class, name = CandidateDao.TYPE),
    @JsonSubTypes.Type(value = NoteDao.class, name = NoteDao.TYPE),
    @JsonSubTypes.Type(value = NviPeriodDao.class, name = NviPeriodDao.TYPE),
    @JsonSubTypes.Type(value = CandidateUniquenessEntryDao.class, name = CandidateUniquenessEntryDao.TYPE)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public abstract class Dao implements DynamoEntryWithRangeKey {

    @DynamoDbIgnore
    public Map<String, AttributeValue> toDynamoFormat() {
        return EnhancedDocument.fromJson(attempt(() -> dynamoObjectMapper.writeValueAsString(this)).orElseThrow())
                   .toMap();
    }
}
