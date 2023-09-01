package no.sikt.nva.nvi.common.db.converters;

import no.sikt.nva.nvi.common.model.business.NviPeriod;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;

public class NviPeriodConverterProvider implements AttributeConverterProvider {

    public static final NviPeriodConverter NVI_PERIOD_CONVERTER = new NviPeriodConverter();

    @Override
    public <T> AttributeConverter<T> converterFor(EnhancedType<T> enhancedType) {
        if (parametricTypeIsPeriod(enhancedType)) {
            return (AttributeConverter<T>) NVI_PERIOD_CONVERTER;
        }

        return null;
    }

    private <T> boolean parametricTypeIsPeriod(EnhancedType<T> enhancedType) {
        return EnhancedType.of(NviPeriod.class).equals(enhancedType);
    }

}
