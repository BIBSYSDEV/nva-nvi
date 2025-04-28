package no.sikt.nva.nvi.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.PublicationDetails;

// FIXME: Suppressing temporarily.
// Many fields of this class can be replaced with a single PublicationDto
@SuppressWarnings("PMD.TooManyFields")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record UpsertNviCandidateRequest(
    URI publicationId,
    URI publicationBucketUri,
    InstanceType instanceType,
    String abstractText,
    PageCountDto pageCount,
    @JsonProperty("publicationDate") PublicationDateDto date,
    List<VerifiedNviCreatorDto> verifiedCreators,
    List<UnverifiedNviCreatorDto> unverifiedCreators,
    String channelType,
    URI publicationChannelId,
    String level,
    BigDecimal basePoints,
    boolean isInternationalCollaboration,
    BigDecimal collaborationFactor,
    int creatorShareCount,
    List<InstitutionPoints> institutionPoints,
    BigDecimal totalPoints)
    implements CandidateType {

  public boolean isApplicable() {
    return true;
  }

  public PublicationDetails.PublicationDate publicationDate() {
    return new PublicationDetails.PublicationDate(date.year(), date.month(), date.day());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI publicationId;
    private URI publicationBucketUri;
    private InstanceType instanceType;
    private String abstractText;
    private PageCountDto pageCount;
    private PublicationDateDto date;
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

    public Builder withInstanceType(InstanceType instanceType) {
      this.instanceType = instanceType;
      return this;
    }

    public Builder withAbstract(String abstractText) {
      this.abstractText = abstractText;
      return this;
    }

    public Builder withPageCount(PageCountDto pageCount) {
      this.pageCount = pageCount;
      return this;
    }

    public Builder withDate(PublicationDateDto date) {
      this.date = date;
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
          instanceType,
          abstractText,
          pageCount,
          date,
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
