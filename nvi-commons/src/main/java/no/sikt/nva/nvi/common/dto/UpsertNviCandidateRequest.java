package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record UpsertNviCandidateRequest(
    URI publicationBucketUri,
    PointCalculationDto pointCalculation,
    PublicationDto publicationDetails,
    // TODO: Merge lists of verified and unverified creators
    List<VerifiedNviCreatorDto> verifiedCreators,
    List<UnverifiedNviCreatorDto> unverifiedCreators)
    implements CandidateType {

  public void validate() {
    publicationDetails.validate();
    pointCalculation.validate();
    shouldNotBeNull(publicationBucketUri, "Required field 'publicationBucketUri' is null");
    shouldNotBeNull(verifiedCreators, "Required field 'verifiedCreators' is null");
    shouldNotBeNull(unverifiedCreators, "Required field 'unverifiedCreators' is null");
  }

  @Override
  public URI publicationId() {
    return publicationDetails.id();
  }

  public boolean isApplicable() {
    return true;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI publicationBucketUri;
    private PointCalculationDto pointCalculation;
    private PublicationDto publicationDetails;
    private List<VerifiedNviCreatorDto> verifiedNviCreators = Collections.emptyList();
    private List<UnverifiedNviCreatorDto> unverifiedNviCreators = Collections.emptyList();

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

    public Builder withVerifiedNviCreators(List<VerifiedNviCreatorDto> verifiedNviCreators) {
      this.verifiedNviCreators = verifiedNviCreators;
      return this;
    }

    public Builder withUnverifiedNviCreators(List<UnverifiedNviCreatorDto> unverifiedNviCreators) {
      this.unverifiedNviCreators = unverifiedNviCreators;
      return this;
    }

    public UpsertNviCandidateRequest build() {
      return new UpsertNviCandidateRequest(
          publicationBucketUri,
          pointCalculation,
          publicationDetails,
          verifiedNviCreators,
          unverifiedNviCreators);
    }
  }
}
