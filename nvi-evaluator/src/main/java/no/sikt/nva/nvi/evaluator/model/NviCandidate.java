package no.sikt.nva.nvi.evaluator.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record NviCandidate(URI publicationId,
                           URI publicationBucketUri,
                           String instanceType,
                           PublicationDate publicationDate,
                           List<Creator> verifiedCreators,
                           String channelType,
                           URI publicationChannelId,
                           String level,
                           BigDecimal basePoints,
                           boolean isInternationalCollaboration,
                           BigDecimal collaborationFactor,
                           int creatorShareCount,
                           Map<URI, BigDecimal> institutionPoints,
                           BigDecimal totalPoints) implements CandidateType {

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
        private URI publicationBucketUri;
        private String instanceType;
        private PublicationDate publicationDate;
        private List<Creator> verifiedCreators;
        private String channelType;
        private URI publicationChannelId;
        private String level;
        private BigDecimal basePoints;
        private boolean isInternationalCollaboration;
        private BigDecimal collaborationFactor;
        private int creatorShareCount;
        private Map<URI, BigDecimal> institutionPoints;
        private BigDecimal totalPoints;

        private Builder() {
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        public Builder withPublicationBucketUri(URI publicationBucketUri) {
            this.publicationBucketUri = publicationBucketUri;
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

        public Builder withChannelType(String channelType) {
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

        public Builder withCreatorShareCount(int creatorShareCount) {
            this.creatorShareCount = creatorShareCount;
            return this;
        }

        public Builder withInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
            this.institutionPoints = institutionPoints;
            return this;
        }

        public Builder withTotalPoints(BigDecimal totalPoints) {
            this.totalPoints = totalPoints;
            return this;
        }

        public NviCandidate build() {
            return new NviCandidate(publicationId, publicationBucketUri, instanceType, publicationDate,
                                    verifiedCreators, channelType, publicationChannelId, level, basePoints,
                                    isInternationalCollaboration, collaborationFactor, creatorShareCount,
                                    institutionPoints, totalPoints);
        }
    }
}