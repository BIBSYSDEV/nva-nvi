package no.sikt.nva.nvi.rest;

import static java.math.BigDecimal.ZERO;
import static no.sikt.nva.nvi.test.TestUtils.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.UpsertRequestBuilder;
import no.unit.nva.auth.uriretriever.UriRetriever;

public final class TestUtils {
  public static final URI DEFAULT_VERIFIED_CREATOR_ID =
      URI.create("https://www.example.com/verifiedCreator");
  public static final String DEFAULT_UNVERIFIED_CREATOR_NAME = "Unverified Creator";
  public static final URI DEFAULT_TOP_LEVEL_INSTITUTION_ID =
      URI.create("https://www.example.com/toplevelOrganization");
  public static final URI DEFAULT_SUB_UNIT_INSTITUTION_ID =
      URI.create("https://www.example.com/subOrganization");
  public static final UriRetriever mockUriRetriever = mock(UriRetriever.class);
  public static final OrganizationRetriever mockOrganizationRetriever =
      new OrganizationRetriever(mockUriRetriever);

  // TODO: Should not have static org.retriever? Better to recreate for each test?

  private TestUtils() {}

  public static VerifiedNviCreatorDto setupDefaultVerifiedCreator() {
    return setupVerifiedCreator(
        DEFAULT_VERIFIED_CREATOR_ID,
        List.of(DEFAULT_SUB_UNIT_INSTITUTION_ID),
        DEFAULT_TOP_LEVEL_INSTITUTION_ID);
  }

  public static UnverifiedNviCreatorDto setupDefaultUnverifiedCreator() {
    return setupUnverifiedCreator(
        DEFAULT_UNVERIFIED_CREATOR_NAME,
        List.of(DEFAULT_SUB_UNIT_INSTITUTION_ID),
        DEFAULT_TOP_LEVEL_INSTITUTION_ID);
  }

  public static VerifiedNviCreatorDto setupVerifiedCreator(
      URI id, Collection<URI> affiliations, URI topLevelInstitutionId) {
    affiliations.forEach(
        affiliation ->
            mockOrganizationResponseForAffiliation(
                topLevelInstitutionId, affiliation, mockUriRetriever));
    return new VerifiedNviCreatorDto(id, List.copyOf(affiliations));
  }

  public static UnverifiedNviCreatorDto setupUnverifiedCreator(
      String name, Collection<URI> affiliations, URI topLevelInstitutionId) {
    affiliations.forEach(
        affiliation ->
            mockOrganizationResponseForAffiliation(
                topLevelInstitutionId, affiliation, mockUriRetriever));
    return new UnverifiedNviCreatorDto(name, List.copyOf(affiliations));
  }

  public static UpsertRequestBuilder createUpsertCandidateRequestWithPoints(
      Map<URI, Collection<NviCreatorDto>> nviCreatorsPerInstitution) {
    var institutionPoints =
        nviCreatorsPerInstitution.entrySet().stream().map(TestUtils::getCreatorPoints).toList();
    var totalPoints =
        institutionPoints.stream()
            .map(InstitutionPoints::institutionPoints)
            .reduce(ZERO, BigDecimal::add);
    var verifiedCreators =
        nviCreatorsPerInstitution.values().stream()
            .flatMap(Collection::stream)
            .filter(VerifiedNviCreatorDto.class::isInstance)
            .map(VerifiedNviCreatorDto.class::cast)
            .toList();
    var unverifiedCreators =
        nviCreatorsPerInstitution.values().stream()
            .flatMap(Collection::stream)
            .filter(UnverifiedNviCreatorDto.class::isInstance)
            .map(UnverifiedNviCreatorDto.class::cast)
            .toList();

    var creatorMap =
        nviCreatorsPerInstitution.entrySet().stream()
            .flatMap(
                entry ->
                    entry.getValue().stream()
                        .filter(VerifiedNviCreatorDto.class::isInstance)
                        .map(VerifiedNviCreatorDto.class::cast)
                        .map(creator -> Map.entry(creator.id(), creator.affiliations())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    return randomUpsertRequestBuilder()
        .withVerifiedCreators(verifiedCreators)
        .withUnverifiedCreators(unverifiedCreators)
        .withCreators(creatorMap)
        .withPoints(institutionPoints)
        .withTotalPoints(totalPoints);
  }

  public static Candidate upsertCandidate(
      CandidateRepository candidateRepository,
      PeriodRepository periodRepository,
      UpsertCandidateRequest request) {
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }

  public static UpdateStatusRequest createStatusRequest(ApprovalStatus status) {
    return UpdateStatusRequest.builder()
        .withInstitutionId(DEFAULT_TOP_LEVEL_INSTITUTION_ID)
        .withApprovalStatus(status)
        .withUsername(randomString())
        .withReason(ApprovalStatus.REJECTED.equals(status) ? randomString() : null)
        .build();
  }

  public static Candidate setupCandidateWithVerifiedCreator(
      CandidateRepository candidateRepository, PeriodRepository periodRepository) {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var request =
        createUpsertCandidateRequestWithPoints(
                Map.of(DEFAULT_TOP_LEVEL_INSTITUTION_ID, List.of(verifiedCreator)))
            .build();
    return upsertCandidate(candidateRepository, periodRepository, request);
  }

  public static Candidate setupCandidateWithUnverifiedCreator(
      CandidateRepository candidateRepository, PeriodRepository periodRepository) {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var unverifiedCreator = setupDefaultUnverifiedCreator();
    var request =
        createUpsertCandidateRequestWithPoints(
                Map.of(
                    DEFAULT_TOP_LEVEL_INSTITUTION_ID, List.of(verifiedCreator, unverifiedCreator)))
            .build();
    return upsertCandidate(candidateRepository, periodRepository, request);
  }

  public static Candidate setupCandidateWithApproval(
      CandidateRepository candidateRepository, PeriodRepository periodRepository) {
    // Create a candidate in a "valid" state
    var verifiedCreator = setupDefaultVerifiedCreator();
    var initialRequest =
        createUpsertCandidateRequestWithPoints(
                Map.of(DEFAULT_TOP_LEVEL_INSTITUTION_ID, List.of(verifiedCreator)))
            .build();
    var candidate = upsertCandidate(candidateRepository, periodRepository, initialRequest);

    // Approve the candidate
    var updateStatusRequest = createStatusRequest(ApprovalStatus.APPROVED);
    return candidate.updateApprovalStatus(updateStatusRequest, mockOrganizationRetriever);
  }

  public static Candidate setupCandidateWithUnverifiedCreatorFromAnotherInstitution(
      CandidateRepository candidateRepository, PeriodRepository periodRepository) {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var otherInstitutionId = randomUri();
    var unverifiedCreator =
        setupUnverifiedCreator(randomString(), List.of(otherInstitutionId), otherInstitutionId);
    var request =
        createUpsertCandidateRequestWithPoints(
                Map.of(
                    DEFAULT_TOP_LEVEL_INSTITUTION_ID, List.of(verifiedCreator, unverifiedCreator)))
            .build();
    return upsertCandidate(candidateRepository, periodRepository, request);
  }

  private static InstitutionPoints getCreatorPoints(
      Map.Entry<URI, Collection<NviCreatorDto>> entry) {
    var institution = entry.getKey();
    var creators = entry.getValue();
    var creatorPoints = getCreatorPoints(creators);
    var pointsPerInstitution =
        creatorPoints.stream().map(CreatorAffiliationPoints::points).reduce(ZERO, BigDecimal::add);
    return new InstitutionPoints(institution, pointsPerInstitution, creatorPoints);
  }

  private static List<CreatorAffiliationPoints> getCreatorPoints(
      Collection<NviCreatorDto> creators) {
    return creators.stream()
        .filter(VerifiedNviCreatorDto.class::isInstance)
        .map(VerifiedNviCreatorDto.class::cast)
        .map(TestUtils::getCreatorPoints)
        .toList();
  }

  private static CreatorAffiliationPoints getCreatorPoints(VerifiedNviCreatorDto creator) {
    return new CreatorAffiliationPoints(
        creator.id(), creator.affiliations().getFirst(), randomBigDecimal());
  }
}
