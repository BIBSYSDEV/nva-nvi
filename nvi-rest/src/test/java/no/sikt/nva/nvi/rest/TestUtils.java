package no.sikt.nva.nvi.rest;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.net.URI;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;

public final class TestUtils {
  public static final URI DEFAULT_TOP_LEVEL_INSTITUTION_ID =
      URI.create("https://www.example.com/toplevelOrganization");

  private TestUtils() {}

  public static UpdateStatusRequest createStatusRequest(ApprovalStatus status) {
    return UpdateStatusRequest.builder()
        .withInstitutionId(DEFAULT_TOP_LEVEL_INSTITUTION_ID)
        .withApprovalStatus(status)
        .withUsername(randomString())
        .withReason(ApprovalStatus.REJECTED.equals(status) ? randomString() : null)
        .build();
  }
}
