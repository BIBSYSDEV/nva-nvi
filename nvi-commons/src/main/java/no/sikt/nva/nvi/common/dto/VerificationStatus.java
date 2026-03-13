package no.sikt.nva.nvi.common.dto;

import static nva.commons.core.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import no.sikt.nva.nvi.common.exceptions.ValidationException;

public record VerificationStatus(String value) {

  private static final String VERIFIED = "Verified";

  public VerificationStatus {
    if (isBlank(value)) {
      throw new ValidationException("Verification status cannot be blank");
    }
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static VerificationStatus fromJson(Object json) {
    if (json instanceof String stringValue) {
      return new VerificationStatus(stringValue);
    }
    if (json instanceof List<?> list && !list.isEmpty()) {
      return new VerificationStatus(list.getFirst().toString());
    }
    throw new ValidationException("Cannot deserialize VerificationStatus from: " + json);
  }

  public boolean isVerified() {
    return VERIFIED.equalsIgnoreCase(value);
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
