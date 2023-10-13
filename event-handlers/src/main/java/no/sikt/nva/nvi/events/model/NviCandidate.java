package no.sikt.nva.nvi.events.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record NviCandidate(URI publicationId,
                           String instanceType,
                           PublicationDate publicationDate,
                           List<Creator> verifiedCreators,
                           String level,
                           Map<URI, BigDecimal> institutionPoints) implements CandidateType {

    public static Builder builder() {
        return new Builder();
    }

    public record Creator(URI id,
                          List<URI> nviInstitutions) {

    }

    public record PublicationDate(String day,
                                  String month,
                                  String year) {

    }

    public static final class Builder {

        private URI publicationId;
        private String instanceType;
        private PublicationDate publicationDate;
        private List<Creator> verifiedCreators;
        private String level;
        private Map<URI, BigDecimal> institutionPoints;

        private Builder() {
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        public Builder withInstanceType(String instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public Builder withPublicationDate(PublicationDate publicationDate) {
            this.publicationDate = publicationDate;
            return this;
        }

        public Builder withVerifiedCreators(List<Creator> verifiedCreators) {
            this.verifiedCreators = verifiedCreators;
            return this;
        }

        public Builder withLevel(String level) {
            this.level = level;
            return this;
        }

        public Builder withInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
            this.institutionPoints = institutionPoints;
            return this;
        }

        public NviCandidate build() {
            return new NviCandidate(publicationId, instanceType, publicationDate, verifiedCreators, level,
                                    institutionPoints);
        }
    }
}