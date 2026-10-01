package no.sikt.nva.nvi.common.dto;

import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;

/**
 * The person behind a contribution. Verified identities have an ID, unverified ones only a name.
 */
@JsonSerialize
public record IdentityDto(URI id, String name, URI orcid, VerificationStatus verificationStatus) {

  public IdentityDto {
    verificationStatus = requireNonNullElse(verificationStatus, VerificationStatus.NOT_VERIFIED);
  }
}
