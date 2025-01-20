package no.sikt.nva.nvi.events.evaluator.model;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;

public record VerifiedNviCreator(URI id, List<NviOrganization> nviAffiliations)
    implements NviCreator {

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public List<URI> nviAffiliationsIds() {
    return nviAffiliations.stream().map(NviOrganization::id).toList();
  }

  @Override
  public boolean isAffiliatedWith(URI institutionId) {
    return nviAffiliations.stream().anyMatch(isNviOrganization(institutionId));
  }

  public Collection<URI> getAffiliationsPartOf(URI institutionId) {
    return nviAffiliations.stream()
        .filter(isNviOrganization(institutionId))
        .map(NviOrganization::id)
        .toList();
  }

  private static Predicate<NviOrganization> isNviOrganization(URI institutionId) {
    return affiliation -> affiliation.isPartOf(institutionId);
  }

  public VerifiedNviCreatorDto toDto() {
    return VerifiedNviCreatorDto.builder()
        .withId(id)
        .withAffiliations(nviAffiliationsIds())
        .build();
  }

  public static final class Builder {

    private URI id;

    private List<NviOrganization> nviAffiliations;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withNviAffiliations(List<NviOrganization> nviAffiliations) {
      this.nviAffiliations = nviAffiliations;
      return this;
    }

    public VerifiedNviCreator build() {
      return new VerifiedNviCreator(id, nviAffiliations);
    }
  }
}
