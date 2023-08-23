package no.sikt.nva.nvi.common.db;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import no.sikt.nva.nvi.common.db.dto.CandidateDb;
import nva.commons.core.attempt.Try;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class CandidateConverter implements AttributeConverter<CandidateDb> {

    @Override
    public AttributeValue transformFrom(CandidateDb input) {
        var json = dtoObjectMapper.valueToTree(input);
        return AttributeValue.builder().s(json.toString()).build(); //TODO: Store as map instead
    }

    @Override
    public CandidateDb transformTo(AttributeValue input) {
        var text = input.s();
        return Try.attempt(() -> dtoObjectMapper.readValue(text, CandidateDb.class)).orElseThrow() ;
    }

    @Override
    public EnhancedType<CandidateDb> type() {
        return EnhancedType.of(CandidateDb.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S; //TODO: Store as map instead
    }
}
