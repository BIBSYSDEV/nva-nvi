package no.sikt.nva.nvi.common.db.converters;

import no.sikt.nva.nvi.common.model.business.NviPeriod;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class NviPeriodConverter implements AttributeConverter<NviPeriod> {

    @Override
    public AttributeValue transformFrom(NviPeriod input) {
        return input.toDynamoDb();
    }

    @Override
    public NviPeriod transformTo(AttributeValue input) {
        return NviPeriod.builder().build().fromDynamoDb(input, NviPeriod.class);
    }

    @Override
    @JacocoGenerated
    public EnhancedType<NviPeriod> type() {
        return EnhancedType.of(NviPeriod.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.M;
    }
}
