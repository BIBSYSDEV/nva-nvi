package no.sikt.nva.nvi.common.dto;

import static nva.commons.core.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonValue;

public record VerificationStatus(String value) {

  private static final String VERIFIED = "Verified";

  public VerificationStatus {
    if (isBlank(value)) {
      throw new IllegalArgumentException("Verification status cannot be blank");
    }
  }

  public boolean isVerified() {
    return VERIFIED.equalsIgnoreCase(value);
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
