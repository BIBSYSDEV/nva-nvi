package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getGlobalEnvironment;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.NTNU_ID;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.NTNU_LABELS;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.SIKT_ID;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.SIKT_LABELS;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.SIKT_SUBUNIT_ID;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.TOP_LEVEL_ORGANIZATION_NTNU;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.TOP_LEVEL_ORGANIZATION_SIKT;
import static no.sikt.nva.nvi.common.examples.ExamplePublicationChannels.JOURNAL_OF_TESTING;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.ROLE_CREATOR;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.ROLE_OTHER;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.STATUS_UNVERIFIED;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.STATUS_VERIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PageCountDto;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.model.PointCalculation;
import no.sikt.nva.nvi.common.model.PublicationChannel;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.common.service.model.PageCount;
import no.sikt.nva.nvi.common.service.model.PublicationDetails;
import no.sikt.nva.nvi.common.service.model.Username;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import org.junit.jupiter.api.Test;

class CandidateToIndexDocumentMapperTest {

  private static final String VALID_IDENTIFIER =
      "01892ad6fc07-4ddfbfc5-3145-4b06-9f77-d4202d074380";
  private static final URI PUBLICATION_ID =
      URI.create("https://api.fake.nva.aws.unit.no/publication/" + VALID_IDENTIFIER);
  private static final URI CREATOR_ID =
      URI.create("https://api.fake.nva.aws.unit.no/cristin/person/12345");
  private static final String CREATOR_NAME = "Ola Nordmann";
  private static final String UNVERIFIED_CREATOR_NAME = "Kari Nordmann";
  private static final String PUBLICATION_TITLE = "Test Publication";
  private static final String ABSTRACT_TEXT = "Test abstract";
  private static final String LANGUAGE = "http://lexvo.org/id/iso639-3/nob";

  @Test
  void shouldProduceValidIndexDocument() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    assertThat(document).isNotNull();
    assertThat(document.identifier()).isNotNull();
    assertThat(document.isApplicable()).isTrue();
    assertThat(document.publicationDetails()).isNotNull();
    assertThat(document.publicationDetails().title()).isEqualTo(PUBLICATION_TITLE);
    assertThat(document.publicationDetails().abstractText()).isEqualTo(ABSTRACT_TEXT);
    assertThat(document.publicationDetails().language()).isEqualTo(LANGUAGE);
    assertThat(document.publicationDetails().type())
        .isEqualTo(InstanceType.ACADEMIC_ARTICLE.getValue());
    assertThat(document.points()).isNotNull();
    assertThat(document.creatorShareCount()).isEqualTo(2);
    assertThat(document.reported()).isFalse();
  }

  @Test
  void shouldDerivePartOfChainFromHasPartTree() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    var nviContributors = document.publicationDetails().nviContributors();
    assertThat(nviContributors).isNotEmpty();

    var firstContributor = nviContributors.getFirst();
    var subunitAffiliations =
        firstContributor.nviAffiliations().stream()
            .filter(org -> org.id().equals(SIKT_SUBUNIT_ID))
            .toList();
    assertThat(subunitAffiliations).hasSize(1);

    var subunitAffiliation = subunitAffiliations.getFirst();
    assertThat(subunitAffiliation.partOf()).containsExactly(SIKT_ID);
  }

  @Test
  void shouldReturnEmptyPartOfWhenAffiliationIsTopLevelOrg() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    var nviContributors = document.publicationDetails().nviContributors();
    var firstContributor = nviContributors.getFirst();

    var topLevelAffiliations =
        firstContributor.nviAffiliations().stream()
            .filter(org -> org.id().equals(NTNU_ID))
            .toList();
    assertThat(topLevelAffiliations).hasSize(1);
    assertThat(topLevelAffiliations.getFirst().partOf()).isEmpty();
  }

  @Test
  void shouldExtractLabelsFromCandidateTopLevelOrganizations() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    var siktApproval =
        document.approvals().stream()
            .filter(a -> a.institutionId().equals(SIKT_ID))
            .findFirst()
            .orElseThrow();
    assertThat(siktApproval.labels()).isEqualTo(SIKT_LABELS);

    var ntnuApproval =
        document.approvals().stream()
            .filter(a -> a.institutionId().equals(NTNU_ID))
            .findFirst()
            .orElseThrow();
    assertThat(ntnuApproval.labels()).isEqualTo(NTNU_LABELS);
  }

  @Test
  void shouldPopulateChannelNameFromPublicationDto() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    assertThat(document.publicationDetails().publicationChannel().name())
        .isEqualTo(JOURNAL_OF_TESTING.name());
  }

  @Test
  void shouldPopulateChannelPrintIssnFromPublicationDto() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    assertThat(document.publicationDetails().publicationChannel().printIssn())
        .isEqualTo(JOURNAL_OF_TESTING.printIssn());
  }

  @Test
  void shouldPopulateContributorRolesFromPublicationDto() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    var contributors = document.publicationDetails().contributors();
    assertThat(contributors).isNotEmpty();
    assertThat(contributors).allSatisfy(c -> assertThat(c.role()).isNotNull());
  }

  @Test
  void shouldIncludeNonNviContributorsFromPublicationDto() {
    var candidate = createDefaultCandidate();
    var nonNviContributor =
        ContributorDto.builder()
            .withId(URI.create("https://example.org/person/non-nvi"))
            .withName("Non-NVI Person")
            .withRole(ROLE_OTHER)
            .withVerificationStatus(STATUS_VERIFIED)
            .withAffiliations(List.of(Organization.builder().withId(SIKT_ID).build()))
            .build();
    var publicationDto =
        createPublicationDtoBuilder()
            .withContributors(List.of(createVerifiedCreatorContributor(), nonNviContributor))
            .build();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    var contributors = document.publicationDetails().contributors();
    var nonNviContributors = contributors.stream().filter(Contributor.class::isInstance).toList();
    assertThat(nonNviContributors).hasSize(1);
    assertThat(nonNviContributors.getFirst().name()).isEqualTo("Non-NVI Person");
    assertThat(nonNviContributors.getFirst().role()).isEqualTo(ROLE_OTHER.getValue());
  }

  @Test
  void shouldSetContributorsCountFromCandidate() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    assertThat(document.publicationDetails().contributorsCount())
        .isEqualTo(candidate.publicationDetails().creatorCount());
  }

  @Test
  void shouldMapPendingUnassignedApprovalToNewStatus() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    var siktApproval =
        document.approvals().stream()
            .filter(a -> a.institutionId().equals(SIKT_ID))
            .findFirst()
            .orElseThrow();
    assertThat(siktApproval.approvalStatus()).isEqualTo(ApprovalStatus.NEW);
  }

  @Test
  void shouldMapPendingAssignedApprovalToPendingStatus() {
    var candidateId = UUID.randomUUID();
    var assignedApproval = createAssignedApproval(candidateId, SIKT_ID);
    var unassignedApproval = Approval.createNewApproval(candidateId, NTNU_ID);
    var approvals = Map.of(SIKT_ID, assignedApproval, NTNU_ID, unassignedApproval);
    var candidate = createCandidateWithApprovals(candidateId, approvals);
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    var siktApproval =
        document.approvals().stream()
            .filter(a -> a.institutionId().equals(SIKT_ID))
            .findFirst()
            .orElseThrow();
    assertThat(siktApproval.approvalStatus()).isEqualTo(ApprovalStatus.PENDING);
    assertThat(siktApproval.assignee()).isEqualTo("test-user");
  }

  @Test
  void shouldMatchChannelByTypeWhenIdIsNull() {
    var channel = new PublicationChannel(null, ChannelType.JOURNAL, ScientificValue.LEVEL_ONE);
    var candidate = createCandidateWithChannel(channel);
    var channelDto =
        PublicationChannelDto.builder()
            .withId(URI.create("https://example.org/channel/123"))
            .withChannelType(ChannelType.JOURNAL)
            .withScientificValue(ScientificValue.LEVEL_ONE)
            .withName("Matched by type")
            .withPrintIssn("1234-5678")
            .build();
    var publicationDto =
        createPublicationDtoBuilder().withPublicationChannels(List.of(channelDto)).build();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    assertThat(document.publicationDetails().publicationChannel().name())
        .isEqualTo("Matched by type");
    assertThat(document.publicationDetails().publicationChannel().printIssn())
        .isEqualTo("1234-5678");
  }

  @Test
  void shouldHandleNullChannelTypeGracefully() {
    var channel = new PublicationChannel(null, null, ScientificValue.LEVEL_ONE);
    var candidate = createCandidateWithChannel(channel);
    var publicationDto = createPublicationDtoBuilder().withPublicationChannels(List.of()).build();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    assertThat(document.publicationDetails().publicationChannel()).isNotNull();
    assertThat(document.publicationDetails().publicationChannel().type()).isNull();
    assertThat(document.publicationDetails().publicationChannel().name()).isNull();
    assertThat(document.publicationDetails().publicationChannel().printIssn()).isNull();
  }

  @Test
  void shouldHandleMultipleInstitutionsAndApprovals() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    assertThat(document.approvals()).hasSize(2);
    assertThat(document.numberOfApprovals()).isEqualTo(2);

    var institutionIds = document.approvals().stream().map(ApprovalView::institutionId).toList();
    assertThat(institutionIds).containsExactlyInAnyOrder(SIKT_ID, NTNU_ID);
  }

  @Test
  void shouldIncludeUnverifiedNviCreators() {
    var candidateId = UUID.randomUUID();
    var unverifiedCreator =
        UnverifiedNviCreatorDto.builder()
            .withName(UNVERIFIED_CREATOR_NAME)
            .withAffiliations(List.of(NTNU_ID))
            .build();
    var verifiedCreator =
        VerifiedNviCreatorDto.builder()
            .withId(CREATOR_ID)
            .withName(CREATOR_NAME)
            .withAffiliations(List.of(SIKT_SUBUNIT_ID, NTNU_ID))
            .build();
    var topLevelOrgs = List.of(TOP_LEVEL_ORGANIZATION_SIKT, TOP_LEVEL_ORGANIZATION_NTNU);
    var nviCreators =
        List.of(
            NviCreator.from(verifiedCreator, topLevelOrgs),
            NviCreator.from(unverifiedCreator, topLevelOrgs));
    var candidate = createCandidateWithCreators(candidateId, nviCreators, topLevelOrgs);

    var unverifiedContributor =
        ContributorDto.builder()
            .withName(UNVERIFIED_CREATOR_NAME)
            .withRole(ROLE_CREATOR)
            .withVerificationStatus(STATUS_UNVERIFIED)
            .withAffiliations(List.of(Organization.builder().withId(NTNU_ID).build()))
            .build();
    var publicationDto =
        createPublicationDtoBuilder()
            .withContributors(List.of(createVerifiedCreatorContributor(), unverifiedContributor))
            .build();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    var nviContributors = document.publicationDetails().nviContributors();
    assertThat(nviContributors).hasSize(2);

    var unverifiedNviContributor =
        nviContributors.stream()
            .filter(c -> UNVERIFIED_CREATOR_NAME.equals(c.name()))
            .findFirst()
            .orElseThrow();
    assertThat(unverifiedNviContributor.id()).isNull();
    assertThat(unverifiedNviContributor.name()).isEqualTo(UNVERIFIED_CREATOR_NAME);
  }

  @Test
  void shouldPopulatePagesFromCandidate() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    var pages = document.publicationDetails().pages();
    assertThat(pages).isNotNull();
    assertThat(pages.begin()).isEqualTo("1");
    assertThat(pages.end()).isEqualTo("42");
    assertThat(pages.numberOfPages()).isEqualTo("42");
  }

  @Test
  void shouldPopulatePublicationDateFromCandidate() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    var publicationDate = document.publicationDetails().publicationDate();
    assertThat(publicationDate).isNotNull();
    assertThat(publicationDate.year()).isEqualTo("2025");
    assertThat(publicationDate.month()).isEqualTo("3");
    assertThat(publicationDate.day()).isEqualTo("24");
  }

  @Test
  void shouldSetGlobalApprovalStatusOnApprovals() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    assertThat(document.globalApprovalStatus()).isEqualTo(GlobalApprovalStatus.PENDING);
    document
        .approvals()
        .forEach(
            approval ->
                assertThat(approval.globalApprovalStatus())
                    .isEqualTo(GlobalApprovalStatus.PENDING));
  }

  @Test
  void shouldBuildNviOrganizationWithCorrectPartOfForNviAffiliations() {
    var candidate = createDefaultCandidate();
    var publicationDto = createDefaultPublicationDto();

    var document = new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();

    var nviContributors = document.publicationDetails().nviContributors();
    var firstContributor = nviContributors.getFirst();

    var nviAffiliations = firstContributor.nviAffiliations();
    assertThat(nviAffiliations).isNotEmpty();

    var allAffiliationTypes = firstContributor.affiliations();
    var nviOrgs = allAffiliationTypes.stream().filter(NviOrganization.class::isInstance).toList();
    assertThat(nviOrgs).isNotEmpty();
  }

  // --- Helper methods ---

  private static Candidate createDefaultCandidate() {
    var candidateId = UUID.randomUUID();
    var approvals =
        Map.of(
            SIKT_ID, Approval.createNewApproval(candidateId, SIKT_ID),
            NTNU_ID, Approval.createNewApproval(candidateId, NTNU_ID));
    return createCandidateWithApprovals(candidateId, approvals);
  }

  private static Candidate createCandidateWithApprovals(
      UUID candidateId, Map<URI, Approval> approvals) {
    var topLevelOrgs = List.of(TOP_LEVEL_ORGANIZATION_SIKT, TOP_LEVEL_ORGANIZATION_NTNU);
    var verifiedCreator =
        VerifiedNviCreatorDto.builder()
            .withId(CREATOR_ID)
            .withName(CREATOR_NAME)
            .withAffiliations(List.of(SIKT_SUBUNIT_ID, NTNU_ID))
            .build();
    var nviCreators = List.of(NviCreator.from(verifiedCreator, topLevelOrgs));
    return buildCandidate(candidateId, approvals, nviCreators, topLevelOrgs);
  }

  private static Candidate createCandidateWithCreators(
      UUID candidateId, List<NviCreator> nviCreators, List<Organization> topLevelOrgs) {
    var institutionIds = topLevelOrgs.stream().map(Organization::id).toList();
    var approvals =
        institutionIds.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    id -> id, id -> Approval.createNewApproval(candidateId, id)));
    return buildCandidate(candidateId, approvals, nviCreators, topLevelOrgs);
  }

  private static Candidate createCandidateWithChannel(PublicationChannel channel) {
    var candidateId = UUID.randomUUID();
    var topLevelOrgs = List.of(TOP_LEVEL_ORGANIZATION_SIKT, TOP_LEVEL_ORGANIZATION_NTNU);
    var verifiedCreator =
        VerifiedNviCreatorDto.builder()
            .withId(CREATOR_ID)
            .withName(CREATOR_NAME)
            .withAffiliations(List.of(SIKT_SUBUNIT_ID, NTNU_ID))
            .build();
    var nviCreators = List.of(NviCreator.from(verifiedCreator, topLevelOrgs));
    var approvals =
        Map.of(
            SIKT_ID, Approval.createNewApproval(candidateId, SIKT_ID),
            NTNU_ID, Approval.createNewApproval(candidateId, NTNU_ID));
    return createCandidate(candidateId, approvals, nviCreators, topLevelOrgs, channel);
  }

  private static Candidate buildCandidate(
      UUID candidateId,
      Map<URI, Approval> approvals,
      Collection<NviCreator> nviCreators,
      List<Organization> topLevelOrgs) {

    var channel =
        new PublicationChannel(
            JOURNAL_OF_TESTING.id(), ChannelType.JOURNAL, ScientificValue.LEVEL_ONE);
    return createCandidate(candidateId, approvals, nviCreators, topLevelOrgs, channel);
  }

  private static Candidate createCandidate(
      UUID candidateId,
      Map<URI, Approval> approvals,
      Collection<NviCreator> nviCreators,
      List<Organization> topLevelOrgs,
      PublicationChannel channel) {
    var creatorAffiliationPoints =
        List.of(
            new CreatorAffiliationPoints(CREATOR_ID, SIKT_SUBUNIT_ID, BigDecimal.ONE),
            new CreatorAffiliationPoints(CREATOR_ID, NTNU_ID, BigDecimal.ONE));
    var institutionPoints =
        List.<InstitutionPoints>of(
            new InstitutionPoints(
                SIKT_ID, BigDecimal.TEN, Sector.UHI, List.of(creatorAffiliationPoints.getFirst())),
            new InstitutionPoints(
                NTNU_ID, BigDecimal.TEN, Sector.UHI, List.of(creatorAffiliationPoints.getLast())));
    var pointCalculation =
        new PointCalculation(
            InstanceType.ACADEMIC_ARTICLE,
            channel,
            false,
            BigDecimal.ONE,
            BigDecimal.TEN,
            2,
            institutionPoints,
            BigDecimal.TEN);

    var publicationDetails =
        PublicationDetails.builder()
            .withId(PUBLICATION_ID)
            .withIdentifier(VALID_IDENTIFIER)
            .withTitle(PUBLICATION_TITLE)
            .withAbstract(ABSTRACT_TEXT)
            .withLanguage(LANGUAGE)
            .withStatus("PUBLISHED")
            .withPublicationDate(new PublicationDate("2025", "3", "24"))
            .withPageCount(new PageCount("1", "42", "42"))
            .withPublicationChannel(channel)
            .withNviCreators(nviCreators)
            .withCreatorCount(5)
            .withTopLevelOrganizations(topLevelOrgs)
            .withModifiedDate(Instant.now())
            .build();

    return new Candidate(
        candidateId,
        true,
        approvals,
        Map.of(),
        createOpenPeriod(),
        pointCalculation,
        publicationDetails,
        Instant.now(),
        Instant.now(),
        null,
        null,
        null,
        getGlobalEnvironment());
  }

  private static NviPeriod createOpenPeriod() {
    return NviPeriod.builder()
        .withId(URI.create("https://api.fake.nva.aws.unit.no/scientific-index/period/2025"))
        .withPublishingYear(2025)
        .withStartDate(Instant.now().minus(30, ChronoUnit.DAYS))
        .withReportingDate(Instant.now().plus(365, ChronoUnit.DAYS))
        .withCreatedBy(Username.fromString("admin"))
        .build();
  }

  private static Approval createAssignedApproval(UUID candidateId, URI institutionId) {
    return new Approval(
        candidateId,
        institutionId,
        no.sikt.nva.nvi.common.service.model.ApprovalStatus.PENDING,
        Username.fromString("test-user"),
        null,
        null,
        null,
        null);
  }

  private static PublicationDto createDefaultPublicationDto() {
    return createPublicationDtoBuilder().build();
  }

  private static PublicationDto.Builder createPublicationDtoBuilder() {
    return PublicationDto.builder()
        .withId(PUBLICATION_ID)
        .withIdentifier(VALID_IDENTIFIER)
        .withTitle(PUBLICATION_TITLE)
        .withAbstract(ABSTRACT_TEXT)
        .withLanguage(LANGUAGE)
        .withStatus("PUBLISHED")
        .withPublicationDate(new PublicationDateDto("2025", "3", "24"))
        .withPageCount(new PageCountDto("1", "42", "42"))
        .withPublicationType(InstanceType.ACADEMIC_ARTICLE)
        .withIsApplicable(true)
        .withIsInternationalCollaboration(false)
        .withPublicationChannels(List.of(JOURNAL_OF_TESTING))
        .withContributors(List.of(createVerifiedCreatorContributor()))
        .withTopLevelOrganizations(
            List.of(TOP_LEVEL_ORGANIZATION_SIKT, TOP_LEVEL_ORGANIZATION_NTNU))
        .withModifiedDate(Instant.now());
  }

  private static ContributorDto createVerifiedCreatorContributor() {
    return ContributorDto.builder()
        .withId(CREATOR_ID)
        .withName(CREATOR_NAME)
        .withRole(ROLE_CREATOR)
        .withVerificationStatus(STATUS_VERIFIED)
        .withAffiliations(
            List.of(
                Organization.builder().withId(SIKT_SUBUNIT_ID).build(),
                Organization.builder().withId(NTNU_ID).build()))
        .build();
  }
}
