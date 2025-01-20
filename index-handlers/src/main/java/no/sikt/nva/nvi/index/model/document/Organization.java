package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("Organization")
public record Organization(URI id, List<URI> partOf) implements OrganizationType {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI id;
    private List<URI> partOf;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withPartOf(List<URI> partOf) {
      this.partOf = partOf;
      return this;
    }

    public Organization build() {
      return new Organization(id, partOf);
    }
  }
}
