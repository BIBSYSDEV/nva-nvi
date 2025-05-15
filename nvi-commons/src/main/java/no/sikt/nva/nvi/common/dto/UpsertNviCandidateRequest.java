package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record UpsertNviCandidateRequest(
    URI publicationBucketUri,
    PointCalculationDto pointCalculation,
    PublicationDto publicationDetails,
    Collection<NviCreatorDto> nviCreators,
    Collection<Organization> topLevelNviOrganizations)
    implements CandidateType {

  private static final boolean ALWAYS_APPLICABLE = true;

  public void validate() {
    publicationDetails.validate();
    pointCalculation.validate();
    shouldNotBeNull(publicationBucketUri, "Required field 'publicationBucketUri' is null");
    shouldNotBeNull(nviCreators, "Required field 'nviCreators' is null");
  }

  @Override
  public URI publicationId() {
    return publicationDetails.id();
  }

  public boolean isApplicable() {
    return ALWAYS_APPLICABLE;
  }

  public List<VerifiedNviCreatorDto> verifiedCreators() {
    return nviCreators.stream()
        .filter(VerifiedNviCreatorDto.class::isInstance)
        .map(VerifiedNviCreatorDto.class::cast)
        .toList();
  }

  public List<UnverifiedNviCreatorDto> unverifiedCreators() {
    return nviCreators.stream()
        .filter(UnverifiedNviCreatorDto.class::isInstance)
        .map(UnverifiedNviCreatorDto.class::cast)
        .toList();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI publicationBucketUri;
    private PointCalculationDto pointCalculation;
    private PublicationDto publicationDetails;
    private final List<NviCreatorDto> nviCreators = new ArrayList<>();
    private final List<Organization> topLevelNviOrganizations = new ArrayList<>();

    private Builder() {}

    public Builder withPublicationBucketUri(URI publicationBucketUri) {
      this.publicationBucketUri = publicationBucketUri;
      return this;
    }

    public Builder withPointCalculation(PointCalculationDto pointCalculation) {
      this.pointCalculation = pointCalculation;
      return this;
    }

    public Builder withPublicationDetails(PublicationDto publicationDetails) {
      this.publicationDetails = publicationDetails;
      return this;
    }

    public Builder withNviCreators(Collection<NviCreatorDto> nviCreators) {
      this.nviCreators.addAll(nviCreators);
      return this;
    }

    public Builder withVerifiedNviCreators(Collection<VerifiedNviCreatorDto> verifiedNviCreators) {
      this.nviCreators.addAll(verifiedNviCreators);
      return this;
    }

    public Builder withUnverifiedNviCreators(
        Collection<UnverifiedNviCreatorDto> unverifiedNviCreators) {
      this.nviCreators.addAll(unverifiedNviCreators);
      return this;
    }

    public Builder withTopLevelNviOrganizations(Collection<Organization> topLevelNviOrganizations) {
      this.topLevelNviOrganizations.addAll(topLevelNviOrganizations);
      return this;
    }

    public UpsertNviCandidateRequest build() {
      return new UpsertNviCandidateRequest(
          publicationBucketUri,
          pointCalculation,
          publicationDetails,
          nviCreators,
          topLevelNviOrganizations);
    }
  }
}
