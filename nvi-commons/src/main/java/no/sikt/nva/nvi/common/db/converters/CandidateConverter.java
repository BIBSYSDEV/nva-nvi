package no.sikt.nva.nvi.common.db.converters;

import no.sikt.nva.nvi.common.model.business.Candidate;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class CandidateConverter implements AttributeConverter<Candidate> {

    @Override
    public AttributeValue transformFrom(Candidate input) {
        return input.toDynamoDb();
    }

    @Override
    public Candidate transformTo(AttributeValue input) {
        return Candidate.fromDynamoDb(input);
    }

    @Override
    @JacocoGenerated
    public EnhancedType<Candidate> type() {
        return EnhancedType.of(Candidate.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.M;
    }
}
