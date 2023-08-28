package no.sikt.nva.nvi.common.db;

import no.sikt.nva.nvi.common.model.business.Candidate;
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
        return EnhancedType.of(Candidate.class).equals(enhancedType);
    }

}
