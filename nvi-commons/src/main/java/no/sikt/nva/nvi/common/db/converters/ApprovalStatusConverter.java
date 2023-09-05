package no.sikt.nva.nvi.common.db.converters;

import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class ApprovalStatusConverter implements AttributeConverter<ApprovalStatus> {

    @Override
    public AttributeValue transformFrom(ApprovalStatus input) {
        return input.toDynamoDb();
    }

    @Override
    public ApprovalStatus transformTo(AttributeValue input) {
        return ApprovalStatus.builder().build().fromDynamoDb(input, ApprovalStatus.class);
    }

    @Override
    @JacocoGenerated
    public EnhancedType<ApprovalStatus> type() {
        return EnhancedType.of(ApprovalStatus.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.M;
    }
}
