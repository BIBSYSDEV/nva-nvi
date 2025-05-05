package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record UpsertNviCandidateRequest(
    URI publicationId, // FIXME: Remove this
    URI publicationBucketUri,
    PublicationDto publicationDetails,
    List<VerifiedNviCreatorDto> verifiedCreators,
    List<UnverifiedNviCreatorDto> unverifiedCreators,
    String channelType, // FIXME: Merge this
    URI publicationChannelId,
    String level,
    BigDecimal basePoints,
    boolean isInternationalCollaboration,
    BigDecimal collaborationFactor,
    int creatorShareCount,
    List<InstitutionPoints> institutionPoints,
    BigDecimal totalPoints)
    implements CandidateType {

  public void validate() {
    publicationDetails.validate();
    shouldNotBeNull(publicationBucketUri, "Required field 'publicationBucketUri' is null");
    shouldNotBeNull(creatorShareCount, "Required field 'creatorShareCount' is null");
    shouldNotBeNull(verifiedCreators, "Required field 'verifiedCreators' is null");
    shouldNotBeNull(unverifiedCreators, "Required field 'unverifiedCreators' is null");
    shouldNotBeNull(institutionPoints, "Required field 'institutionPoints' is null");
    shouldNotBeNull(totalPoints, "Required field 'totalPoints' is null");
  }

  public boolean isApplicable() {
    return true;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI publicationId;
    private URI publicationBucketUri;
    private PublicationDto publicationDetails;
    private List<VerifiedNviCreatorDto> verifiedNviCreators = Collections.emptyList();
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

    private Builder() {}

    public Builder withPublicationId(URI publicationId) {
      this.publicationId = publicationId;
      return this;
    }

    public Builder withPublicationBucketUri(URI publicationBucketUri) {
      this.publicationBucketUri = publicationBucketUri;
      return this;
    }

    public Builder withPublicationDetails(PublicationDto publicationDetails) {
      this.publicationDetails = publicationDetails;
      return this;
    }

    public Builder withVerifiedNviCreators(List<VerifiedNviCreatorDto> verifiedNviCreators) {
      this.verifiedNviCreators = verifiedNviCreators;
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

    public UpsertNviCandidateRequest build() {
      return new UpsertNviCandidateRequest(
          publicationId,
          publicationBucketUri,
          publicationDetails,
          verifiedNviCreators,
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
