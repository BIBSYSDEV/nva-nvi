package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoFrom;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.test.TestUtils;

public class UpsertRequestFixtures {

  public static UpsertNonNviCandidateRequest createUpsertNonCandidateRequest(URI publicationId) {
    return new UpsertNonNviCandidateRequest(publicationId);
  }

  public static UpdateStatusRequest createUpdateStatusRequest(
      ApprovalStatus status, URI institutionId, String username) {
    return UpdateStatusRequest.builder()
        .withReason(ApprovalStatus.REJECTED.equals(status) ? randomString() : null)
        .withApprovalStatus(status)
        .withInstitutionId(institutionId)
        .withUsername(username)
        .build();
  }

  public static UpsertRequestBuilder createUpsertCandidateRequest(URI... organizations) {
    var verifiedNviCreators = new ArrayList<VerifiedNviCreatorDto>();
    var institutionPoints = new ArrayList<InstitutionPoints>();
    for (var organizationId : organizations) {
      var pointValue = TestUtils.randomBigDecimal();
      var creator = verifiedNviCreatorDtoFrom(organizationId);
      var creatorPoint = new CreatorAffiliationPoints(creator.id(), organizationId, pointValue);
      var institutionPoint =
          new InstitutionPoints(organizationId, pointValue, List.of(creatorPoint));
      verifiedNviCreators.add(creator);
      institutionPoints.add(institutionPoint);
    }

    return randomUpsertRequestBuilder()
        .withVerifiedCreators(verifiedNviCreators)
        .withPoints(institutionPoints);
  }

  public static UpsertNviCandidateRequest createUpsertCandidateRequest(
      URI topLevelOrg, URI affiliation) {
    var creator = verifiedNviCreatorDtoFrom(affiliation);
    var points = TestUtils.randomBigDecimal();
    var institutionPoints =
        List.of(
            new InstitutionPoints(
                topLevelOrg,
                points,
                List.of(new CreatorAffiliationPoints(creator.id(), affiliation, points))));

    return randomUpsertRequestBuilder()
        .withVerifiedCreators(List.of(creator))
        .withPoints(institutionPoints)
        .build();
  }
}
