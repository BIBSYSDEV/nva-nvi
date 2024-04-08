package no.sikt.nva.nvi.events.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.Creator;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.events.model.NviCandidate.NviCreatorWithAffiliationPoints.AffiliationPoints;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record NviCandidate(URI publicationId,
                           URI publicationBucketUri,
                           String instanceType,
                           @JsonProperty("publicationDate") PublicationDate date,
                           List<NviCreatorWithAffiliationPoints> nviCreatorWithAffiliationPoints,
                           String channelType,
                           URI publicationChannelId,
                           String level,
                           BigDecimal basePoints,
                           boolean isInternationalCollaboration,
                           BigDecimal collaborationFactor,
                           int creatorShareCount,
                           Map<URI, BigDecimal> institutionPoints,
                           BigDecimal totalPoints) implements CandidateType, UpsertCandidateRequest {

    public static Builder builder() {
        return new Builder();
    }

    public static NviCandidate fromCandidate(Candidate candidate) {
        var details = candidate.getPublicationDetails();

        return NviCandidate.builder()
                   .withPublicationId(details.publicationId())
                   .withPublicationBucketUri(details.publicationBucketUri())
                   .withInstanceType(details.type())
                   .withDate(toPublicationDate(details.publicationDate()))
                   .withVerifiedCreators(details.creators().stream()
                                             .map(NviCandidate::mapToNviCreator)
                                             .toList())
                   .withChannelType(details.channelType().getValue())
                   .withPublicationChannelId(details.publicationChannelId())
                   .withLevel(details.level())
                   .withBasePoints(candidate.getBasePoints())
                   .withIsInternationalCollaboration(candidate.isInternationalCollaboration())
                   .withCollaborationFactor(candidate.getCollaborationFactor())
                   .withCreatorShareCount(candidate.getCreatorShareCount())
                   .withInstitutionPoints(candidate.getInstitutionPoints())
                   .withTotalPoints(candidate.getTotalPoints())
                   .build();
    }

    @Override
    public boolean isApplicable() {
        return true;
    }

    @Override
    public Map<URI, List<URI>> creators() {
        return nviCreatorWithAffiliationPoints().stream()
                   .collect(Collectors.toMap(NviCreatorWithAffiliationPoints::id,
                                             creator -> creator.affiliationPoints().stream()
                                                            .map(AffiliationPoints::affiliationId)
                                                            .toList()));
    }

    @Override
    public PublicationDetails.PublicationDate publicationDate() {
        return mapToPublicationDate(date);
    }

    private static NviCreatorWithAffiliationPoints mapToNviCreator(Creator creator) {
        return new NviCreatorWithAffiliationPoints(creator.id(), mapToAffiliationPoints(creator.affiliations()));
    }

    private static List<AffiliationPoints> mapToAffiliationPoints(List<URI> affiliations) {
        //TODO: Implement when Candidate contains AffiliationPoints
        return affiliations.stream().map(affiliation -> new AffiliationPoints(affiliation, null))
                   .toList();
    }

    private static NviCandidate.PublicationDate toPublicationDate(
        no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate publicationDate) {
        return new NviCandidate.PublicationDate(
            publicationDate.day(),
            publicationDate.month(),
            publicationDate.year());
    }

    private static PublicationDetails.PublicationDate mapToPublicationDate(
        PublicationDate publicationDate) {
        return new PublicationDetails.PublicationDate(publicationDate.year(),
                                                      publicationDate.month(),
                                                      publicationDate.day());
    }

    public record NviCreatorWithAffiliationPoints(URI id, List<AffiliationPoints> affiliationPoints) {

        public record AffiliationPoints(URI affiliationId, BigDecimal points) {

        }
    }

    public record PublicationDate(String day, String month, String year) {

    }

    public static final class Builder {

        private URI publicationId;
        private URI publicationBucketUri;
        private String instanceType;
        private PublicationDate date;
        private List<NviCreatorWithAffiliationPoints> nviCreatorWithAffiliationPoints;
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

        public Builder withDate(PublicationDate date) {
            this.date = date;
            return this;
        }

        public Builder withVerifiedCreators(List<NviCreatorWithAffiliationPoints> nviCreatorWithAffiliationPoints) {
            this.nviCreatorWithAffiliationPoints = nviCreatorWithAffiliationPoints;
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
            return new NviCandidate(publicationId, publicationBucketUri, instanceType, date,
                                    nviCreatorWithAffiliationPoints,
                                    channelType, publicationChannelId, level, basePoints, isInternationalCollaboration,
                                    collaborationFactor, creatorShareCount, institutionPoints, totalPoints);
        }
    }
}