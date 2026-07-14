package no.sikt.nva.nvi.common.dto;

import static nva.commons.core.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonValue;
import no.sikt.nva.nvi.common.exceptions.ValidationException;

public record ContributorRole(String value) {

  public static final ContributorRole CREATOR = new ContributorRole("Creator");
  public static final ContributorRole EDITOR = new ContributorRole("Editor");

  public ContributorRole {
    if (isBlank(value)) {
      throw new ValidationException("Contributor role cannot be blank");
    }
  }

  public boolean isCreator() {
    return CREATOR.value().equalsIgnoreCase(value);
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
