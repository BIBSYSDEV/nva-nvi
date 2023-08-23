package no.sikt.nva.nvi.common.db;

import java.util.Set;
import no.sikt.nva.nvi.common.db.dto.CandidateDb;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;

public class CandidateConverterProvider implements AttributeConverterProvider {

    public static final CandidateConverter CANDIDATE_CONVERTER = new CandidateConverter();

    @Override
    public <T> AttributeConverter<T> converterFor(EnhancedType<T> enhancedType) {
        if (parametricTypeIsCandidate(enhancedType)) {
            return (AttributeConverter<T>) CANDIDATE_CONVERTER;
        }

        return null;
    }

    private <T> boolean parametricTypeIsCandidate(EnhancedType<T> enhancedType) {
        var v1 = EnhancedType.of(CandidateDb.class);
        var v2= enhancedType.rawClass();
        return EnhancedType.of(CandidateDb.class).equals(enhancedType);
    }

    private <T> boolean isSet(EnhancedType<T> enhancedType) {
        return Set.class.equals(enhancedType.rawClass());
    }
}
