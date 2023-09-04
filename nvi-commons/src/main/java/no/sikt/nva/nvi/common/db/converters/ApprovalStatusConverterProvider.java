package no.sikt.nva.nvi.common.db.converters;

import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;

public class ApprovalStatusConverterProvider implements AttributeConverterProvider {

    private static final ApprovalStatusConverter APPROVAL_STATUS_CONVERTER = new ApprovalStatusConverter();

    @Override
    public <T> AttributeConverter<T> converterFor(EnhancedType<T> enhancedType) {
        if (parametricTypeIsApprovalStatus(enhancedType)) {
            return (AttributeConverter<T>) APPROVAL_STATUS_CONVERTER;
        }
        return null;
    }

    private <T> boolean parametricTypeIsApprovalStatus(EnhancedType<T> enhancedType) {
        return EnhancedType.of(ApprovalStatus.class).equals(enhancedType);
    }
}
