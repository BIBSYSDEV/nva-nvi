package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.common.model.ContributorFixtures.ROLE_CREATOR;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.ROLE_OTHER;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.STATUS_VERIFIED;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.createOrganizationHierarchy;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganization;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.model.OrganizationFixtures;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.sikt.nva.nvi.common.model.SampleCandidateGenerator;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CandidateIndexDocumentGeneratorTest {

  @Test
  void shouldGenerateDocumentWithCandidateMetadata() {
    var candidate = minimalCandidate();
    var publicationDto = minimalPublicationDto(candidate);

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    assertEquals(candidate.identifier(), document.identifier());
    assertEquals(candidate.getId(), document.id());
    assertNotNull(document.publicationDetails());
    assertNotNull(document.reportingPeriod());
  }

  @Test
  void shouldPopulatePublicationDetailsFromCandidate() {
    var candidate = minimalCandidate();
    var publicationDto = minimalPublicationDto(candidate);

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var details = document.publicationDetails();
    assertEquals(candidate.publicationDetails().publicationId().toString(), details.id());
    assertEquals(candidate.publicationDetails().title(), details.title());
    assertEquals(candidate.getPublicationType().getValue(), details.type());
  }

  @Test
  void shouldPopulateHandlesFromCandidate() {
    var expectedHandles = Set.of(randomUri(), randomUri());
    var publicationDetails =
        PublicationDetails.builder()
            .withId(randomUri())
            .withTitle(randomString())
            .withPublicationDate(new PublicationDate(randomYear(), null, null))
            .withNviCreators(List.of())
            .withHandles(expectedHandles)
            .build();
    var candidate =
        new SampleCandidateGenerator().withPublicationDetails(publicationDetails).build();
    var publicationDto = minimalPublicationDto(candidate);

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    assertThat(document.publicationDetails().handles())
        .containsExactlyInAnyOrderElementsOf(expectedHandles);
  }

  @ParameterizedTest
  @EnumSource(
      value = Sector.class,
      names = {"UNKNOWN"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldPopulateSectorInApprovalViewWhenSectorIsNotUnknown(Sector sector) {
    var institutionId = randomUri();
    var candidate = candidateWithInstitutionSector(institutionId, sector);
    var publicationDto = minimalPublicationDto(candidate);

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var approval = getApprovalView(document.approvals(), institutionId);
    assertEquals(sector.toString(), approval.sector());
  }

  @Test
  void shouldNotPopulateSectorInApprovalViewWhenSectorIsUnknown() {
    var institutionId = randomUri();
    var candidate = candidateWithInstitutionSector(institutionId, Sector.UNKNOWN);
    var publicationDto = minimalPublicationDto(candidate);

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var approval = getApprovalView(document.approvals(), institutionId);
    assertNull(approval.sector());
  }

  @Test
  void shouldPopulateApprovalStatusAsNewWhenPendingAndUnassigned() {
    var institutionId = randomUri();
    var candidate = candidateWithInstitutionSector(institutionId, Sector.UHI);
    var publicationDto = minimalPublicationDto(candidate);

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var approval = getApprovalView(document.approvals(), institutionId);
    assertEquals(ApprovalStatus.NEW, approval.approvalStatus());
  }

  @Test
  void shouldPopulateLabelsFromCandidateTopLevelOrganizations() {
    var topLevelOrgId = randomUri();
    var expectedLabels = Map.of("nb", "Testorganisasjon", "en", "Test Organization");
    var topLevelOrg =
        Organization.builder()
            .withId(topLevelOrgId)
            .withLabels(expectedLabels)
            .withCountryCode("NO")
            .build();

    var nviCreator =
        VerifiedNviCreatorDto.builder()
            .withId(randomUri())
            .withName(randomString())
            .withAffiliations(List.of(topLevelOrgId))
            .build();
    var nviCreatorModel = NviCreator.from(nviCreator, List.of(topLevelOrg));

    var publicationDetails =
        PublicationDetails.builder()
            .withId(randomUri())
            .withTitle(randomString())
            .withPublicationDate(new PublicationDate(randomYear(), null, null))
            .withNviCreators(List.of(nviCreatorModel))
            .withTopLevelOrganizations(List.of(topLevelOrg))
            .build();

    var candidate =
        new SampleCandidateGenerator()
            .withPublicationDetails(publicationDetails)
            .withInstitutionPoints(topLevelOrgId, Sector.UHI, randomBigDecimal())
            .build();

    var contributorDto = createContributorDto(nviCreator.id(), nviCreatorModel.name(), topLevelOrg);
    var publicationDto = createPublicationDto(candidate, List.of(contributorDto));

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var approval = getApprovalView(document.approvals(), topLevelOrgId);
    assertEquals(expectedLabels, approval.labels());
  }

  @Test
  void shouldBuildNviContributorWithAffiliationPartOfChain() {
    var topLevelOrgId = OrganizationFixtures.randomOrganizationId();
    var departmentId = OrganizationFixtures.randomOrganizationId();
    var subDepartmentId = OrganizationFixtures.randomOrganizationId();

    var topLevelOrg = createOrganizationHierarchy(topLevelOrgId, departmentId, subDepartmentId);

    var creatorId = randomUri();
    var nviCreator =
        VerifiedNviCreatorDto.builder()
            .withId(creatorId)
            .withName(randomString())
            .withAffiliations(List.of(subDepartmentId))
            .build();
    var nviCreatorModel = NviCreator.from(nviCreator, List.of(topLevelOrg));

    var publicationDetails =
        PublicationDetails.builder()
            .withId(randomUri())
            .withTitle(randomString())
            .withPublicationDate(new PublicationDate(randomYear(), null, null))
            .withNviCreators(List.of(nviCreatorModel))
            .withTopLevelOrganizations(List.of(topLevelOrg))
            .build();

    var candidate =
        new SampleCandidateGenerator()
            .withPublicationDetails(publicationDetails)
            .withInstitutionPoints(topLevelOrgId, Sector.UHI, randomBigDecimal())
            .build();

    var subDeptAffiliation =
        buildAffiliationWithPartOfChain(subDepartmentId, departmentId, topLevelOrgId);
    var contributorDto = createContributorDto(creatorId, nviCreator.name(), subDeptAffiliation);
    var publicationDto = createPublicationDto(candidate, List.of(contributorDto));

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var nviContributor =
        findNviContributor(document.publicationDetails().contributors(), creatorId);
    assertNotNull(nviContributor);

    var nviAffiliation = nviContributor.nviAffiliations().getFirst();
    assertEquals(subDepartmentId, nviAffiliation.id());
    assertThat(nviAffiliation.partOf()).contains(departmentId, topLevelOrgId);
  }

  @Test
  void shouldBuildRegularContributorForNonNviCreator() {
    var candidate = minimalCandidate();
    var nonCreatorContributor =
        new ContributorDto(
            randomUri(),
            randomString(),
            null,
            STATUS_VERIFIED,
            ROLE_OTHER,
            List.of(randomOrganization().build()));
    var publicationDto = createPublicationDto(candidate, List.of(nonCreatorContributor));

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var contributors = document.publicationDetails().contributors();
    assertThat(contributors).hasSize(1);
    assertThat(contributors.getFirst()).isNotInstanceOf(NviContributor.class);
  }

  @Test
  void shouldIncludeOrcidInContributor() {
    var expectedOrcid = "0000-0001-2345-6789";
    var candidate = minimalCandidate();
    var contributorWithOrcid =
        new ContributorDto(
            randomUri(),
            randomString(),
            expectedOrcid,
            STATUS_VERIFIED,
            ROLE_OTHER,
            List.of(randomOrganization().build()));
    var publicationDto = createPublicationDto(candidate, List.of(contributorWithOrcid));

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var contributor = document.publicationDetails().contributors().getFirst();
    assertEquals(expectedOrcid, contributor.orcid());
  }

  @Test
  void shouldPopulatePublicationChannelFromCandidateAndPublicationDto() {
    var expectedName = "Test Journal";
    var expectedIssn = "1234-5678";

    var candidate = minimalCandidate();
    var channelId = candidate.getPublicationChannel().id();
    var publicationChannelDto =
        PublicationChannelDto.builder()
            .withId(channelId)
            .withChannelType(candidate.getPublicationChannel().channelType())
            .withScientificValue(
                ScientificValue.parse(
                    candidate.getPublicationChannel().scientificValue().getValue()))
            .withName(expectedName)
            .withPrintIssn(expectedIssn)
            .build();

    var publicationDto =
        PublicationDto.builder()
            .withId(candidate.getPublicationId())
            .withStatus("PUBLISHED")
            .withPublicationDate(
                candidate.publicationDetails().publicationDate().toDtoPublicationDate())
            .withPublicationType(candidate.getPublicationType())
            .withPublicationChannels(List.of(publicationChannelDto))
            .withContributors(List.of())
            .withTopLevelOrganizations(List.of())
            .build();

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var channel = document.publicationDetails().publicationChannel();
    assertEquals(expectedName, channel.name());
    assertEquals(expectedIssn, channel.printIssn());
  }

  @Test
  void shouldPopulatePointsAndCalculationFields() {
    var institutionId = randomUri();
    var candidate = candidateWithInstitutionSector(institutionId, Sector.UHI);
    var publicationDto = minimalPublicationDto(candidate);

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    assertNotNull(document.points());
    assertNotNull(document.publicationTypeChannelLevelPoints());
    assertNotNull(document.globalApprovalStatus());
    assertThat(document.creatorShareCount()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void shouldPopulateInvolvedOrganizationsFromNviContributorAffiliations() {
    var topLevelOrgId = OrganizationFixtures.randomOrganizationId();
    var departmentId = OrganizationFixtures.randomOrganizationId();
    var subDepartmentId = OrganizationFixtures.randomOrganizationId();

    var topLevelOrg = createOrganizationHierarchy(topLevelOrgId, departmentId, subDepartmentId);

    var creatorId = randomUri();
    var nviCreator =
        VerifiedNviCreatorDto.builder()
            .withId(creatorId)
            .withName(randomString())
            .withAffiliations(List.of(subDepartmentId))
            .build();
    var nviCreatorModel = NviCreator.from(nviCreator, List.of(topLevelOrg));

    var publicationDetails =
        PublicationDetails.builder()
            .withId(randomUri())
            .withTitle(randomString())
            .withPublicationDate(new PublicationDate(randomYear(), null, null))
            .withNviCreators(List.of(nviCreatorModel))
            .withTopLevelOrganizations(List.of(topLevelOrg))
            .build();

    var candidate =
        new SampleCandidateGenerator()
            .withPublicationDetails(publicationDetails)
            .withInstitutionPoints(topLevelOrgId, Sector.UHI, randomBigDecimal())
            .build();

    var subDeptAffiliation =
        buildAffiliationWithPartOfChain(subDepartmentId, departmentId, topLevelOrgId);
    var contributorDto = createContributorDto(creatorId, nviCreator.name(), subDeptAffiliation);
    var publicationDto = createPublicationDto(candidate, List.of(contributorDto));

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var approval = getApprovalView(document.approvals(), topLevelOrgId);
    assertThat(approval.involvedOrganizations())
        .contains(topLevelOrgId, departmentId, subDepartmentId);
  }

  @Test
  void shouldSkipAffiliationsWithNullId() {
    var candidate = minimalCandidate();
    var affiliationWithoutId = Organization.builder().build();
    var contributorWithNullAffiliation =
        new ContributorDto(
            randomUri(),
            randomString(),
            null,
            STATUS_VERIFIED,
            ROLE_OTHER,
            List.of(affiliationWithoutId));
    var publicationDto = createPublicationDto(candidate, List.of(contributorWithNullAffiliation));

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var contributor = document.publicationDetails().contributors().getFirst();
    assertThat(contributor.affiliations()).isEmpty();
  }

  @Test
  void shouldPopulatePagesFromCandidateWhenPageCountIsPresent() {
    var expectedBegin = "1";
    var expectedEnd = "50";
    var expectedTotal = "50";
    var pageCount =
        new no.sikt.nva.nvi.common.service.model.PageCount(
            expectedBegin, expectedEnd, expectedTotal);
    var publicationDetails =
        PublicationDetails.builder()
            .withId(randomUri())
            .withTitle(randomString())
            .withPublicationDate(new PublicationDate(randomYear(), null, null))
            .withNviCreators(List.of())
            .withPageCount(pageCount)
            .build();
    var candidate =
        new SampleCandidateGenerator().withPublicationDetails(publicationDetails).build();
    var publicationDto = minimalPublicationDto(candidate);

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var pages = document.publicationDetails().pages();
    assertEquals(expectedBegin, pages.begin());
    assertEquals(expectedEnd, pages.end());
    assertEquals(expectedTotal, pages.numberOfPages());
  }

  @Test
  void shouldHandleUnverifiedNviCreatorMatchedByName() {
    var topLevelOrgId = OrganizationFixtures.randomOrganizationId();
    var topLevelOrg = Organization.builder().withId(topLevelOrgId).withCountryCode("NO").build();
    var creatorName = randomString();
    var affiliationId = OrganizationFixtures.randomOrganizationId();

    var unverifiedCreator =
        no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto.builder()
            .withName(creatorName)
            .withAffiliations(List.of(affiliationId))
            .build();
    var nviCreatorModel = NviCreator.from(unverifiedCreator, List.of(topLevelOrg));

    var publicationDetails =
        PublicationDetails.builder()
            .withId(randomUri())
            .withTitle(randomString())
            .withPublicationDate(new PublicationDate(randomYear(), null, null))
            .withNviCreators(List.of(nviCreatorModel))
            .withTopLevelOrganizations(List.of(topLevelOrg))
            .build();

    var candidate =
        new SampleCandidateGenerator()
            .withPublicationDetails(publicationDetails)
            .withInstitutionPoints(topLevelOrgId, Sector.UHI, randomBigDecimal())
            .build();

    var affiliation = buildAffiliationWithPartOfChain(affiliationId, topLevelOrgId);
    var contributorDto =
        new ContributorDto(
            null,
            creatorName,
            null,
            new no.sikt.nva.nvi.common.dto.VerificationStatus("NotVerified"),
            ROLE_CREATOR,
            List.of(affiliation));
    var publicationDto = createPublicationDto(candidate, List.of(contributorDto));

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var nviContributors =
        document.publicationDetails().contributors().stream()
            .filter(NviContributor.class::isInstance)
            .toList();
    assertThat(nviContributors).hasSize(1);
  }

  @Test
  void shouldBuildSimpleAffiliationForNonNviAffiliationOnNviContributor() {
    var topLevelOrgId = OrganizationFixtures.randomOrganizationId();
    var nviAffiliationId = OrganizationFixtures.randomOrganizationId();
    var nonNviAffiliationId = OrganizationFixtures.randomOrganizationId();

    var topLevelOrg = Organization.builder().withId(topLevelOrgId).withCountryCode("NO").build();
    var creatorId = randomUri();
    var nviCreator =
        VerifiedNviCreatorDto.builder()
            .withId(creatorId)
            .withName(randomString())
            .withAffiliations(List.of(nviAffiliationId))
            .build();
    var nviCreatorModel = NviCreator.from(nviCreator, List.of(topLevelOrg));

    var publicationDetails =
        PublicationDetails.builder()
            .withId(randomUri())
            .withTitle(randomString())
            .withPublicationDate(new PublicationDate(randomYear(), null, null))
            .withNviCreators(List.of(nviCreatorModel))
            .withTopLevelOrganizations(List.of(topLevelOrg))
            .build();

    var candidate =
        new SampleCandidateGenerator()
            .withPublicationDetails(publicationDetails)
            .withInstitutionPoints(topLevelOrgId, Sector.UHI, randomBigDecimal())
            .build();

    var nviAffiliation = buildAffiliationWithPartOfChain(nviAffiliationId, topLevelOrgId);
    var nonNviAffiliation = Organization.builder().withId(nonNviAffiliationId).build();
    var contributorDto =
        new ContributorDto(
            creatorId,
            nviCreator.name(),
            null,
            STATUS_VERIFIED,
            ROLE_CREATOR,
            List.of(nviAffiliation, nonNviAffiliation));
    var publicationDto = createPublicationDto(candidate, List.of(contributorDto));

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var nviContributor =
        findNviContributor(document.publicationDetails().contributors(), creatorId);
    assertNotNull(nviContributor);
    assertThat(nviContributor.affiliations()).hasSize(2);

    var nviOrgs =
        nviContributor.affiliations().stream()
            .filter(no.sikt.nva.nvi.index.model.document.NviOrganization.class::isInstance)
            .toList();
    var simpleOrgs =
        nviContributor.affiliations().stream()
            .filter(aff -> aff instanceof no.sikt.nva.nvi.index.model.document.Organization)
            .toList();
    assertThat(nviOrgs).hasSize(1);
    assertThat(simpleOrgs).hasSize(1);
  }

  @Test
  void shouldMapApprovalStatusWhenApprovalIsAssigned() {
    var institutionId = randomUri();
    var candidateIdentifier = java.util.UUID.randomUUID();
    var assignedApproval =
        new no.sikt.nva.nvi.common.service.model.Approval(
            candidateIdentifier,
            institutionId,
            no.sikt.nva.nvi.common.service.model.ApprovalStatus.PENDING,
            new no.sikt.nva.nvi.common.service.model.Username(randomString()),
            null,
            null,
            null,
            null);

    var publicationDetails =
        PublicationDetails.builder()
            .withId(randomUri())
            .withTitle(randomString())
            .withPublicationDate(new PublicationDate(randomYear(), null, null))
            .withNviCreators(List.of())
            .build();

    var candidate =
        new SampleCandidateGenerator()
            .withPublicationDetails(publicationDetails)
            .withInstitutionPoints(institutionId, Sector.UHI, randomBigDecimal())
            .build();

    var candidateWithAssignedApproval =
        new Candidate(
            candidate.identifier(),
            candidate.applicable(),
            Map.of(institutionId, assignedApproval),
            candidate.notes(),
            candidate.period(),
            candidate.pointCalculation(),
            candidate.publicationDetails(),
            candidate.createdDate(),
            candidate.modifiedDate(),
            candidate.reportStatus(),
            candidate.revision(),
            candidate.version(),
            candidate.environment());

    var publicationDto = minimalPublicationDto(candidateWithAssignedApproval);

    var document =
        new CandidateIndexDocumentGenerator(candidateWithAssignedApproval, publicationDto)
            .generateDocument();

    var approval = getApprovalView(document.approvals(), institutionId);
    assertEquals(ApprovalStatus.PENDING, approval.approvalStatus());
  }

  @Test
  void shouldNotMatchUnverifiedCreatorWhenContributorNameIsNull() {
    var candidate = minimalCandidate();
    var contributorWithNullName =
        new ContributorDto(
            null,
            null,
            null,
            new no.sikt.nva.nvi.common.dto.VerificationStatus("NotVerified"),
            ROLE_CREATOR,
            List.of(randomOrganization().build()));
    var publicationDto = createPublicationDto(candidate, List.of(contributorWithNullName));

    var document =
        new CandidateIndexDocumentGenerator(candidate, publicationDto).generateDocument();

    var contributors = document.publicationDetails().contributors();
    assertThat(contributors).hasSize(1);
    assertThat(contributors.getFirst()).isNotInstanceOf(NviContributor.class);
  }

  private static Candidate minimalCandidate() {
    return new SampleCandidateGenerator().build();
  }

  private static Candidate candidateWithInstitutionSector(URI institutionId, Sector sector) {
    return new SampleCandidateGenerator()
        .withInstitutionPoints(institutionId, sector, randomBigDecimal())
        .build();
  }

  private static PublicationDto minimalPublicationDto(Candidate candidate) {
    var channel = candidate.getPublicationChannel();
    var channels =
        java.util.Objects.nonNull(channel.id()) && java.util.Objects.nonNull(channel.channelType())
            ? List.of(
                PublicationChannelDto.builder()
                    .withId(channel.id())
                    .withChannelType(channel.channelType())
                    .withScientificValue(channel.scientificValue())
                    .build())
            : List.<PublicationChannelDto>of();
    return PublicationDto.builder()
        .withId(candidate.getPublicationId())
        .withStatus("PUBLISHED")
        .withPublicationDate(
            candidate.publicationDetails().publicationDate().toDtoPublicationDate())
        .withPublicationType(candidate.getPublicationType())
        .withPublicationChannels(channels)
        .withContributors(List.of())
        .withTopLevelOrganizations(List.of())
        .build();
  }

  private static PublicationDto createPublicationDto(
      Candidate candidate, List<ContributorDto> contributors) {
    var channel = candidate.getPublicationChannel();
    var channels =
        java.util.Objects.nonNull(channel.id()) && java.util.Objects.nonNull(channel.channelType())
            ? List.of(
                PublicationChannelDto.builder()
                    .withId(channel.id())
                    .withChannelType(channel.channelType())
                    .withScientificValue(channel.scientificValue())
                    .build())
            : List.<PublicationChannelDto>of();
    return PublicationDto.builder()
        .withId(candidate.getPublicationId())
        .withStatus("PUBLISHED")
        .withPublicationDate(
            candidate.publicationDetails().publicationDate().toDtoPublicationDate())
        .withPublicationType(candidate.getPublicationType())
        .withPublicationChannels(channels)
        .withContributors(contributors)
        .withTopLevelOrganizations(List.of())
        .build();
  }

  private static ContributorDto createContributorDto(
      URI creatorId, String name, Organization affiliation) {
    return new ContributorDto(
        creatorId, name, null, STATUS_VERIFIED, ROLE_CREATOR, List.of(affiliation));
  }

  private static ApprovalView getApprovalView(List<ApprovalView> approvals, URI institutionId) {
    return approvals.stream()
        .filter(approval -> approval.institutionId().equals(institutionId))
        .findFirst()
        .orElse(null);
  }

  private static NviContributor findNviContributor(
      List<ContributorType> contributors, URI creatorId) {
    return contributors.stream()
        .filter(NviContributor.class::isInstance)
        .map(NviContributor.class::cast)
        .filter(contributor -> creatorId.toString().equals(contributor.id()))
        .findFirst()
        .orElse(null);
  }

  private static Organization buildAffiliationWithPartOfChain(URI affiliationId, URI... parentIds) {
    Organization current = null;
    for (var i = parentIds.length - 1; i >= 0; i--) {
      var partOf = java.util.Objects.isNull(current) ? List.<Organization>of() : List.of(current);
      current = Organization.builder().withId(parentIds[i]).withPartOf(partOf).build();
    }
    var partOf = java.util.Objects.isNull(current) ? List.<Organization>of() : List.of(current);
    return Organization.builder().withId(affiliationId).withPartOf(partOf).build();
  }
}
