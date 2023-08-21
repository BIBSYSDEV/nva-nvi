package no.sikt.nva.nvi.common.db;

import java.util.Set;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;

public class NviCandidateConverterProvider implements AttributeConverterProvider<Set<CandidateDao>> {

    @Override
    public <T> AttributeConverter<T> converterFor(EnhancedType<T> enhancedType) {
        return null;
    }
}
