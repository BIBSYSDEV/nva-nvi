package no.sikt.nva.nvi.evaluator.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record CandidateEvaluatedMessage(
    CandidateType candidate
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private CandidateType candidate;

        private Builder() {
        }

        public Builder withCandidateType(CandidateType candidate) {
            this.candidate = candidate;
            return this;
        }

        public CandidateEvaluatedMessage build() {
            return new CandidateEvaluatedMessage(candidate);
        }
    }
}