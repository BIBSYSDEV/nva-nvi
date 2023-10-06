package no.sikt.nva.nvi.evaluator.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record NviCandidate(CandidateDetails candidateDetails) implements CandidateType {

    @Override
    public URI publicationId() {
        return candidateDetails.publicationId();
    }

    public record CandidateDetails(URI publicationId,
                                   String instanceType,
                                   PublicationDate publicationDate,
                                   List<Creator> verifiedCreators,
                                   PublicationChannel channelType,
                                   URI publicationChannelId,
                                   String level,
                                   BigDecimal basePoints,
                                   boolean isInternationalCollaboration,
                                   BigDecimal collaborationFactor,
                                   Map<URI, BigDecimal> institutionPoints) {

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
            private NviCandidate.CandidateDetails.PublicationDate publicationDate;
            private List<NviCandidate.CandidateDetails.Creator> verifiedCreators;
            private PublicationChannel channelType;
            private URI publicationChannelId;
            private String level;
            private BigDecimal basePoints;
            private boolean isInternationalCollaboration;
            private BigDecimal collaborationFactor;
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

            public Builder withChannelType(PublicationChannel channelType) {
                this.channelType = channelType;
                return this;
            }

            public Builder withPublicationChannelId(URI publicationChannelId) {
                this.publicationChannelId = publicationChannelId;
                return this;
            }

            public Builder withLevel(String level) {
                this.level = level;
                return this;
            }

            public Builder withBasePoints(BigDecimal basePoints) {
                this.basePoints = basePoints;
                return this;
            }

            public Builder withIsInternationalCollaboration(boolean isInternationalCollaboration) {
                this.isInternationalCollaboration = isInternationalCollaboration;
                return this;
            }

            public Builder withCollaborationFactor(BigDecimal collaborationFactor) {
                this.collaborationFactor = collaborationFactor;
                return this;
            }

            public Builder withInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
                this.institutionPoints = institutionPoints;
                return this;
            }

            public CandidateDetails build() {
                return new CandidateDetails(publicationId, instanceType, publicationDate, verifiedCreators, channelType,
                                            publicationChannelId, level, basePoints, isInternationalCollaboration,
                                            collaborationFactor, institutionPoints);
            }
        }
    }
}