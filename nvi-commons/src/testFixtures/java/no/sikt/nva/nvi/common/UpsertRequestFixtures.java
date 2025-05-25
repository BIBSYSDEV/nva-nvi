package no.sikt.nva.nvi.common;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.common.dto.PointCalculationDtoBuilder.randomPointCalculationDtoBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;

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

  public static UpsertRequestBuilder createUpsertCandidateRequest(
      Collection<Organization> topLevelOrganizations) {
    var creators = new ArrayList<NviCreatorDto>();
    var pointCalculation = randomPointCalculationDtoBuilder().withInstitutionPoints(emptyList());

    for (var organization : topLevelOrganizations) {
      var creator = verifiedNviCreatorDtoFrom(organization);
      creators.add(creator);
      pointCalculation =
          pointCalculation.withInstitutionPointFor(organization.id(), randomUri(), creator.id());
    }

    return randomUpsertRequestBuilder()
        .withPointCalculation(pointCalculation.build())
        .withNviCreators(creators)
        .withTopLevelOrganizations(topLevelOrganizations);
  }

  public static UpsertRequestBuilder createUpsertCandidateRequest(Organization... organizations) {
    var topLevelOrganizations = List.of(organizations);
    return createUpsertCandidateRequest(topLevelOrganizations);
  }

  public static UpsertRequestBuilder createUpsertCandidateRequest(URI... organizations) {
    var topLevelOrganizations =
        List.of(organizations).stream()
            .map(Organization.builder()::withId)
            .map(Organization.Builder::build)
            .toList();
    return createUpsertCandidateRequest(topLevelOrganizations);
  }

  public static UpsertNviCandidateRequest createUpsertCandidateRequestWithSingleAffiliation(
      URI topLevelOrg, URI affiliation) {
    var creator = verifiedNviCreatorDtoFrom(affiliation);
    var pointCalculation =
        randomPointCalculationDtoBuilder()
            .withInstitutionPointFor(topLevelOrg, affiliation, creator.id())
            .build();

    return randomUpsertRequestBuilder()
        .withPointCalculation(pointCalculation)
        .withNviCreators(creator)
        .build();
  }
}
