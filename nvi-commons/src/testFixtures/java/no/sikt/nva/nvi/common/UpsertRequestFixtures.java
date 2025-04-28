package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

  public static UpsertRequestBuilder createUpsertCandidateRequest(URI... institutions) {
    var creators =
        IntStream.of(1)
            .mapToObj(i -> randomUri())
            .collect(Collectors.toMap(Function.identity(), e -> List.of(institutions)));
    var verifiedCreatorsAsDto =
        creators.entrySet().stream()
            .map(entry -> new VerifiedNviCreatorDto(entry.getKey(), entry.getValue()))
            .toList();

    var points =
        Arrays.stream(institutions)
            .map(
                institution -> {
                  var institutionPoints = TestUtils.randomBigDecimal();
                  return new InstitutionPoints(
                      institution,
                      institutionPoints,
                      creators.keySet().stream()
                          .map(
                              creator ->
                                  new CreatorAffiliationPoints(
                                      creator, institution, institutionPoints))
                          .toList());
                })
            .toList();

    return randomUpsertRequestBuilder()
        .withVerifiedCreators(verifiedCreatorsAsDto)
        .withPoints(points);
  }

  public static UpsertNviCandidateRequest createUpsertCandidateRequest(
      URI topLevelOrg, URI affiliation) {
    var creatorId = randomUri();
    var verifiedCreators = List.of(new VerifiedNviCreatorDto(creatorId, List.of(affiliation)));
    var points = TestUtils.randomBigDecimal();
    var institutionPoints =
        List.of(
            new InstitutionPoints(
                topLevelOrg,
                points,
                List.of(new CreatorAffiliationPoints(creatorId, affiliation, points))));

    return randomUpsertRequestBuilder()
        .withVerifiedCreators(verifiedCreators)
        .withPoints(institutionPoints)
        .build();
  }
}
