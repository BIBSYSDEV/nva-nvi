package no.sikt.nva.nvi.common.dto;

import static nva.commons.core.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonValue;
import no.sikt.nva.nvi.common.exceptions.ValidationException;

public record VerificationStatus(String value) {

  public static final VerificationStatus NOT_VERIFIED = new VerificationStatus("NotVerified");
  public static final VerificationStatus VERIFIED = new VerificationStatus("Verified");

  public VerificationStatus {
    if (isBlank(value)) {
      throw new ValidationException("Verification status cannot be blank");
    }
  }

  public boolean isVerified() {
    return VERIFIED.value().equalsIgnoreCase(value);
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
