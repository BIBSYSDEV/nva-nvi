package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.UpsertRequestBuilder;
import no.unit.nva.auth.uriretriever.UriRetriever;

public final class TestUtils {
  private static final URI DEFAULT_TOP_LEVEL_INSTITUTION_ID =
      URI.create("https://www.example.com/toplevelOrganization");
  private static final URI DEFAULT_SUB_UNIT_INSTITUTION_ID =
      URI.create("https://www.example.com/subOrganization");

  private TestUtils() {}

  public static Candidate setupDefaultApplicableCandidate(
      CandidateRepository candidateRepository,
      PeriodRepository periodRepository,
      UriRetriever mockUriRetriever,
      int verifiedCreatorCount,
      int unverifiedCreatorCount) {
    mockOrganizationResponseForAffiliation(
        DEFAULT_TOP_LEVEL_INSTITUTION_ID, DEFAULT_SUB_UNIT_INSTITUTION_ID, mockUriRetriever);
    var verifiedCreators =
        Stream.generate(() -> createVerifiedCreatorFrom(DEFAULT_SUB_UNIT_INSTITUTION_ID))
            .limit(verifiedCreatorCount)
            .collect(Collectors.toList());
    var unverifiedCreators =
        Stream.generate(() -> createUnverifiedCreatorFrom(DEFAULT_SUB_UNIT_INSTITUTION_ID))
            .limit(unverifiedCreatorCount)
            .collect(Collectors.toList());
    var upsertRequest = createUpsertCandidateRequest(verifiedCreators, unverifiedCreators).build();
    var candidate = upsertCandidate(candidateRepository, periodRepository, upsertRequest);
    return Candidate.updateNonCandidate(
            createUpsertNonCandidateRequest(candidate.getPublicationId()), candidateRepository)
        .orElseThrow();
  }

  private static UpsertRequestBuilder createUpsertCandidateRequest(
      Collection<VerifiedNviCreatorDto> verifiedNviCreators,
      Collection<UnverifiedNviCreatorDto> unverifiedNviCreators) {
    // Collect all affiliations (institutions)
    var allInstitutions = new HashSet<URI>();
    verifiedNviCreators.forEach(creator -> allInstitutions.addAll(creator.affiliations()));
    unverifiedNviCreators.forEach(creator -> allInstitutions.addAll(creator.affiliations()));

    // Assign random points for each verified affiliation
    var totalPoints = randomBigDecimal();
    var creatorCount = verifiedNviCreators.size() + unverifiedNviCreators.size();
    var pointsPerInstitution =
        totalPoints.divide(BigDecimal.valueOf(allInstitutions.size()), RoundingMode.HALF_UP);
    var pointsPerCreatorAffiliation =
        totalPoints.divide(BigDecimal.valueOf(creatorCount), RoundingMode.HALF_UP);
    var points =
        allInstitutions.stream()
            .map(
                institution -> {
                  var verifiedCreatorPoints =
                      verifiedNviCreators.stream()
                          .filter(creator -> creator.affiliations().contains(institution))
                          .map(
                              creator ->
                                  new CreatorAffiliationPoints(
                                      creator.id(), institution, pointsPerCreatorAffiliation))
                          .toList();
                  return new InstitutionPoints(
                      institution, pointsPerInstitution, verifiedCreatorPoints);
                })
            .toList();

    var creatorMap =
        verifiedNviCreators.stream()
            .collect(
                Collectors.toMap(VerifiedNviCreatorDto::id, VerifiedNviCreatorDto::affiliations));
    return randomUpsertRequestBuilder()
        .withVerifiedCreators(verifiedNviCreators)
        .withCreators(creatorMap)
        .withPoints(points);
  }

  private static VerifiedNviCreatorDto createVerifiedCreatorFrom(URI institutionId) {
    return new VerifiedNviCreatorDto(randomUri(), List.of(institutionId));
  }

  private static UnverifiedNviCreatorDto createUnverifiedCreatorFrom(URI institutionId) {
    return new UnverifiedNviCreatorDto(randomString(), List.of(institutionId));
  }

  private static Candidate upsertCandidate(
      CandidateRepository candidateRepository,
      PeriodRepository periodRepository,
      UpsertCandidateRequest request) {
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }
}
