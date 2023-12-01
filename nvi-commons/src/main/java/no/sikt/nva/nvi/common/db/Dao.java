package no.sikt.nva.nvi.common.db;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@JsonSerialize
@JsonSubTypes({
    @JsonSubTypes.Type(value = ApprovalStatusDao.class, name = ApprovalStatusDao.TYPE),
    @JsonSubTypes.Type(value = CandidateDao.class, name = CandidateDao.TYPE),
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public abstract class Dao implements DynamoEntryWithRangeKey {
    public static Map<String, AttributeValue> toDynamoFormat(Dao dao) {
        return EnhancedDocument.fromJson(attempt(() -> dtoObjectMapper.writeValueAsString(dao)).orElseThrow())
                   .toMap();
    }
}
