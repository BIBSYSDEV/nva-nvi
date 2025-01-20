package no.sikt.nva.nvi.events.evaluator.model;

import java.net.URI;
import java.util.Objects;

public record NviOrganization(URI id, NviOrganization topLevelOrganization) {

  public static Builder builder() {
    return new Builder();
  }

  public NviOrganization topLevelOrganization() {
    return Objects.isNull(topLevelOrganization) ? this : topLevelOrganization;
  }

  public boolean isPartOf(URI institutionId) {
    return topLevelOrganization().id().equals(institutionId);
  }

  public static final class Builder {

    private URI id;

    private NviOrganization topLevelOrganization;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withTopLevelOrganization(NviOrganization topLevelOrganization) {
      this.topLevelOrganization = topLevelOrganization;
      return this;
    }

    public NviOrganization build() {
      return new NviOrganization(id, topLevelOrganization);
    }
  }
}
