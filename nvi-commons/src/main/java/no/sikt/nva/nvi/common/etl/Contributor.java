package no.sikt.nva.nvi.common.etl;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record Contributor(
    URI id, String name, String verificationStatus, String role, List<Organization> affiliations) {
  private static final String VERIFIED = "Verified";
  private static final String CREATOR = "Creator";

  public Contributor {
    requireNonNull(affiliations, "Required field 'affiliations' is null");
  }

  public void validate() {
    if (nonNull(id)) {
      requireNonNull(name, "Both 'id' and 'name' is null, one of these fields must be set");
    }
    requireNonNull(verificationStatus, "Required field 'verificationStatus' is null");
    requireNonNull(role, "Required field 'role' is null");
    requireNonNull(affiliations, "Required field 'affiliations' is null");
  }

  @JsonIgnore
  public boolean isCreator() {
    return nonNull(role) && CREATOR.equalsIgnoreCase(role);
  }

  @JsonIgnore
  public boolean isVerified() {
    return nonNull(id)
        && nonNull(verificationStatus)
        && VERIFIED.equalsIgnoreCase(verificationStatus);
  }
}
