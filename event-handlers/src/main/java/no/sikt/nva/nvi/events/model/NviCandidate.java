package no.sikt.nva.nvi.events.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.PublicationDetails;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record NviCandidate(URI publicationId,
                           URI publicationBucketUri,
                           InstanceType instanceType,
                           @JsonProperty("publicationDate") PublicationDate date,
                           List<NviCreator> nviCreators,
                           List<UnverifiedNviCreatorDto> unverifiedCreators,
                           String channelType,
                           URI publicationChannelId,
                           String level,
                           BigDecimal basePoints,
                           boolean isInternationalCollaboration,
                           BigDecimal collaborationFactor,
                           int creatorShareCount,
                           List<InstitutionPoints> institutionPoints,
                           BigDecimal totalPoints) implements CandidateType, UpsertCandidateRequest {


    @Override
    public boolean isApplicable() {
        return true;
    }

    @Override
    public Map<URI, List<URI>> creators() {
        return nviCreators().stream().collect(Collectors.toMap(NviCreator::id,
                                                               NviCreator::nviAffiliations));
    }

    @Override
    public PublicationDetails.PublicationDate publicationDate() {
        return mapToPublicationDate(date);
    }

    private static PublicationDetails.PublicationDate mapToPublicationDate(
        PublicationDate publicationDate) {
        return new PublicationDetails.PublicationDate(publicationDate.year(),
                                                      publicationDate.month(),
                                                      publicationDate.day());
    }

    // FIXME: Duplicate DTO?
    public record NviCreator(URI id, List<URI> nviAffiliations) {

        public static NviCreator from(VerifiedNviCreator nviCreator) {
            return new NviCreator(nviCreator.id(), nviCreator.nviAffiliationsIds());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI publicationId;
        private URI publicationBucketUri;
        private InstanceType instanceType;
        private PublicationDate date;
        private List<NviCreator> nviCreators = Collections.emptyList();
        private List<UnverifiedNviCreatorDto> unverifiedNviCreators = Collections.emptyList();
        private String channelType;
        private URI publicationChannelId;
        private String level;
        private BigDecimal basePoints;
        private boolean isInternationalCollaboration;
        private BigDecimal collaborationFactor;
        private int creatorShareCount;
        private List<InstitutionPoints> institutionPoints;
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

        public Builder withInstanceType(InstanceType instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public Builder withDate(PublicationDate date) {
            this.date = date;
            return this;
        }

        public Builder withNviCreators(List<NviCreator> nviCreators) {
            this.nviCreators = nviCreators;
            return this;
        }

        public Builder withUnverifiedNviCreators(List<UnverifiedNviCreatorDto> unverifiedNviCreators) {
            this.unverifiedNviCreators = unverifiedNviCreators;
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

        public Builder withInstitutionPoints(List<InstitutionPoints> institutionPoints) {
            this.institutionPoints = institutionPoints;
            return this;
        }

        public Builder withTotalPoints(BigDecimal totalPoints) {
            this.totalPoints = totalPoints;
            return this;
        }

        public NviCandidate build() {
            return new NviCandidate(publicationId,
                                    publicationBucketUri,
                                    instanceType,
                                    date,
                                    nviCreators,
                                    unverifiedNviCreators,
                                    channelType,
                                    publicationChannelId,
                                    level,
                                    basePoints,
                                    isInternationalCollaboration,
                                    collaborationFactor,
                                    creatorShareCount,
                                    institutionPoints,
                                    totalPoints);
        }
    }
}