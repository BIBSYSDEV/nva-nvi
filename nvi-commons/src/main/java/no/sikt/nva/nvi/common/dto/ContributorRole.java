package no.sikt.nva.nvi.common.dto;

import static nva.commons.core.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import no.sikt.nva.nvi.common.exceptions.ValidationException;

public record ContributorRole(String value) {

  private static final String CREATOR = "Creator";

  public ContributorRole {
    if (isBlank(value)) {
      throw new ValidationException("Contributor role cannot be blank");
    }
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ContributorRole fromJson(Object json) {
    if (json instanceof String stringValue) {
      return new ContributorRole(stringValue);
    }
    if (json instanceof List<?> list && !list.isEmpty()) {
      return new ContributorRole(list.getFirst().toString());
    }
    throw new ValidationException("Cannot deserialize ContributorRole from: " + json);
  }

  public boolean isCreator() {
    return CREATOR.equalsIgnoreCase(value);
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
