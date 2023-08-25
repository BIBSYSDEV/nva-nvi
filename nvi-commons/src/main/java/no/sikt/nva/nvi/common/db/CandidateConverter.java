package no.sikt.nva.nvi.common.db;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import no.sikt.nva.nvi.common.model.business.Candidate;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Try;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class CandidateConverter implements AttributeConverter<Candidate> {

    @Override
    public AttributeValue transformFrom(Candidate input) {
        var json = dtoObjectMapper.valueToTree(input);
        return AttributeValue.builder().s(json.toString()).build(); //TODO: Store as map instead
    }

    @Override
    public Candidate transformTo(AttributeValue input) {
        var text = input.s();
        return Try.attempt(() -> dtoObjectMapper.readValue(text, Candidate.class)).orElseThrow();
    }

    @Override
    @JacocoGenerated
    public EnhancedType<Candidate> type() {
        return EnhancedType.of(Candidate.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S; //TODO: Store as map instead
    }
}
