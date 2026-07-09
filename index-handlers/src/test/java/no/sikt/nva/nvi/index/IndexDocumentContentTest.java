package no.sikt.nva.nvi.index;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateDao;
import static no.sikt.nva.nvi.common.db.DbApprovalStatusFixtures.randomApprovalDao;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.DbPointCalculationFixtures.randomPointCalculationBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationDetailsFixtures.randomPublicationBuilder;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.createOrganizationHierarchy;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.organizationNode;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.test.TestConstants.PUBLISHED;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.ContributorRole;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDetailsDtoBuilder;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies the content of the generated index document, driven through the real {@link
 * IndexDocumentHandler} (see {@link IndexDocumentHandlerTestBase}). Each test sets up a Candidate,
 * stubs the publication the generator will load, and asserts on observable fields of the persisted
 * document.
 */
class IndexDocumentContentTest extends IndexDocumentHandlerTestBase {

  @ParameterizedTest
  @EnumSource(value = Sector.class, names = "UNKNOWN", mode = EnumSource.Mode.EXCLUDE)
  void shouldPopulateSectorInApprovalViewWhenSectorIsNotUnknown(Sector sector) {
    var institutionId = randomOrganizationId();
    var candidate = setupCandidateWithInstitutionPoints(institutionId, sector, false);
    stubPublication(candidate);

    var document = generateIndexDocument(candidate);

    assertThat(approvalFor(document, institutionId).sector()).isEqualTo(sector.toString());
  }

  @Test
  void shouldNotPopulateSectorInApprovalViewWhenSectorIsUnknown() {
    var institutionId = randomOrganizationId();
    var candidate = setupCandidateWithInstitutionPoints(institutionId, Sector.UNKNOWN, false);
    stubPublication(candidate);

    var document = generateIndexDocument(candidate);

    assertThat(approvalFor(document, institutionId).sector()).isNull();
  }

  @Test
  void shouldNotPopulateSectorInApprovalViewWhenSectorIsNull() {
    var institutionId = randomOrganizationId();
    var candidate = setupCandidateWithInstitutionPoints(institutionId, null, false);
    stubPublication(candidate);

    var document = generateIndexDocument(candidate);

    assertThat(approvalFor(document, institutionId).sector()).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldPopulateRboInstitutionInIndexDocumentApprovalView(boolean rboInstitution) {
    var institutionId = randomOrganizationId();
    var candidate = setupCandidateWithInstitutionPoints(institutionId, Sector.UHI, rboInstitution);
    stubPublication(candidate);

    var document = generateIndexDocument(candidate);

    assertThat(approvalFor(document, institutionId).rboInstitution()).isEqualTo(rboInstitution);
  }

  @Test
  void shouldUsePublicationLabelsWhenCandidateHasNoLabels() {
    var institutionId = randomOrganizationId();
    var candidate = setupCandidateWithInstitutionPoints(institutionId, Sector.UHI, false);
    var expectedLabels = Map.of("nb", randomString(), "en", randomString());
    var labeledInstitution =
        Organization.builder().withId(institutionId).withLabels(expectedLabels).build();
    stubPublication(
        candidate, publicationDtoWithTopLevelOrganizations(candidate, labeledInstitution));

    var document = generateIndexDocument(candidate);

    assertThat(approvalFor(document, institutionId).labels()).isEqualTo(expectedLabels);
  }

  @Test
  void shouldReturnEmptyApprovalLabelsWhenNeitherCandidateNorPublicationHasLabels() {
    var institutionId = randomOrganizationId();
    var candidate = setupCandidateWithInstitutionPoints(institutionId, Sector.UHI, false);
    var unlabeledInstitution = Organization.builder().withId(institutionId).build();
    stubPublication(
        candidate, publicationDtoWithTopLevelOrganizations(candidate, unlabeledInstitution));

    var document = generateIndexDocument(candidate);

    assertThat(approvalFor(document, institutionId).labels()).isEmpty();
  }

  @Test
  void shouldPopulateHandlesInIndexDocumentFromCandidatePublicationDetails() {
    var expectedHandles = Set.of(randomUri(), randomUri());
    var candidate = setupCandidateWithHandles(expectedHandles);
    stubPublication(candidate);

    var document = generateIndexDocument(candidate);

    assertThat(document.publicationDetails().handles())
        .containsExactlyInAnyOrderElementsOf(expectedHandles);
  }

  @Test
  void shouldIncludeUnverifiedNviCreatorInNviContributors() {
    var expectedName = randomString();
    var candidate = setupCandidateWithUnverifiedCreator(randomOrganizationId(), expectedName);
    stubPublication(candidate);

    var document = generateIndexDocument(candidate);

    assertThat(document.publicationDetails().nviContributors())
        .extracting(NviContributor::name)
        .contains(expectedName);
  }

  @Test
  void shouldMatchChannelByTypeWhenCandidateChannelHasNoId() {
    var channel = publicationChannel(randomString(), ChannelType.SERIES);
    var candidate = candidateWithChannelTypeButNoId(channel.channelType());
    stubPublication(candidate, publicationDtoWithChannel(candidate, channel));

    var document = generateIndexDocument(candidate);

    assertThat(document.publicationDetails().publicationChannel().name())
        .isNotBlank()
        .isEqualTo(channel.name());
  }

  @Test
  void shouldFallBackToChannelTypeWhenCandidateChannelIdHasNoMatchInPublication() {
    var channel = publicationChannel(randomString(), ChannelType.JOURNAL);
    var candidate = candidateWithChannel(channel.id(), channel.channelType());
    stubPublication(candidate, publicationDtoWithChannel(candidate, channel));

    var document = generateIndexDocument(candidate);

    assertThat(document.publicationDetails().publicationChannel().name())
        .isNotBlank()
        .isEqualTo(channel.name());
  }

  /**
   * A channel without ID and type is not a valid state for candidates created in nva-nvi, but it
   * occurs for candidates imported from Cristin.
   */
  @Test
  void shouldBuildIndexDocumentWhenCandidateChannelHasNoIdOrType() {
    var candidate = candidateWithChannel(null, null);
    var unrelatedChannel = publicationChannel(randomString(), ChannelType.JOURNAL);
    stubPublication(candidate, publicationDtoWithChannel(candidate, unrelatedChannel));

    var document = generateIndexDocument(candidate);

    var indexedChannel = document.publicationDetails().publicationChannel();
    assertThat(indexedChannel.scientificValue()).isEqualTo(ScientificValue.LEVEL_ONE.getValue());
    assertThat(indexedChannel.type()).isNull();
    assertThat(indexedChannel.name()).isNull();
    assertThat(indexedChannel.printIssn()).isNull();
  }

  @Test
  void shouldIndexNviCreatorFromCandidateWhenRemovedFromPublication() {
    var institution = organizationNode(randomOrganizationId(), null);
    var creator = verifiedNviCreatorDtoFrom(institution.id());
    var candidate = setupCandidateWithCreator(creator, institution);
    stubPublication(candidate, publicationDtoWithContributors(candidate, emptyList()));

    var document = generateIndexDocument(candidate);

    assertThat(document.publicationDetails().nviContributors())
        .extracting(NviContributor::id)
        .containsExactly(creator.id().toString());
  }

  @Test
  void shouldUseCandidateAffiliationWhenPublicationHasDifferentAffiliation() {
    var topLevelId = randomOrganizationId();
    var departmentId = randomOrganizationId();
    var sectionId = randomOrganizationId();
    var institution = createOrganizationHierarchy(topLevelId, departmentId, sectionId);
    var creator = verifiedNviCreatorDtoFrom(sectionId);
    var candidate = setupCandidateWithCreator(creator, institution);
    var otherAffiliation = organizationNode(randomOrganizationId(), null);
    var movedContributor = publicationContributor(creator.id(), creator.name(), otherAffiliation);
    stubPublication(
        candidate, publicationDtoWithContributors(candidate, List.of(movedContributor)));

    var document = generateIndexDocument(candidate);

    var nviContributor = document.publicationDetails().nviContributors().getFirst();
    assertThat(nviContributor.nviAffiliations())
        .extracting(NviOrganization::id)
        .containsExactly(sectionId);
    assertThat(nviContributor.nviAffiliations().getFirst().partOf())
        .containsExactly(departmentId, topLevelId);
  }

  @Test
  void shouldIndexNviAffiliationAsStandaloneLeafWhenCandidateHasNoOrganizationTree() {
    var affiliation = organizationNode(randomOrganizationId(), randomOrganizationId());
    var creator = verifiedNviCreatorDtoFrom(affiliation.id());
    var candidate = setupCandidateWithoutOrganizationTree(creator);
    var publicationContributor = publicationContributor(creator.id(), creator.name(), affiliation);
    stubPublication(
        candidate, publicationDtoWithContributors(candidate, List.of(publicationContributor)));

    var document = generateIndexDocument(candidate);

    var nviAffiliation =
        document.publicationDetails().nviContributors().getFirst().nviAffiliations().getFirst();
    assertThat(nviAffiliation.id()).isEqualTo(affiliation.id());
    assertThat(nviAffiliation.partOf())
        .as("partOf must not be reconstructed from the live publication")
        .isEmpty();
  }

  @Test
  void shouldFallBackToPublicationNameWhenCandidateCreatorHasNoName() {
    var institution = organizationNode(randomOrganizationId(), null);
    var creator = new VerifiedNviCreatorDto(randomUri(), null, List.of(institution.id()));
    var candidate = setupCandidateWithCreator(creator, institution);
    var expectedName = randomString();
    var publicationContributor = publicationContributor(creator.id(), expectedName, institution);
    stubPublication(
        candidate, publicationDtoWithContributors(candidate, List.of(publicationContributor)));

    var document = generateIndexDocument(candidate);

    assertThat(document.publicationDetails().nviContributors())
        .extracting(NviContributor::name)
        .containsExactly(expectedName);
  }

  @Test
  void shouldIndexPublicationTypeFromCandidateNotFromChangedPublication() {
    var candidate = reportedCandidateWithType(InstanceType.ACADEMIC_ARTICLE);
    var changedPublication = publicationDtoWithType(candidate, InstanceType.ACADEMIC_MONOGRAPH);
    stubPublication(candidate, changedPublication);

    var document = generateIndexDocument(candidate);

    assertThat(document.publicationDetails().type())
        .isEqualTo(InstanceType.ACADEMIC_ARTICLE.getValue());
  }

  private static ApprovalView approvalFor(NviCandidateIndexDocument document, URI institutionId) {
    return document.approvals().stream()
        .filter(approval -> approval.institutionId().equals(institutionId))
        .findFirst()
        .orElseThrow();
  }

  private Candidate setupCandidateWithInstitutionPoints(
      URI institutionId, Sector sector, boolean rboInstitution) {
    var verifiedCreator = verifiedNviCreatorDtoFrom(institutionId);
    var topLevelOrganization = Organization.builder().withId(institutionId).build();
    var institutionPoints =
        buildInstitutionPoints(institutionId, sector, rboInstitution, randomBigDecimal());
    var request =
        randomUpsertRequestBuilder()
            .withCreatorsAndPoints(Map.of(topLevelOrganization, List.of(verifiedCreator)))
            .withPoints(List.of(institutionPoints))
            .build();
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
  }

  private static InstitutionPoints buildInstitutionPoints(
      URI institutionId, Sector sector, boolean rboInstitution, BigDecimal points) {
    var creatorAffiliationPoints =
        new CreatorAffiliationPoints(randomUri(), institutionId, randomBigDecimal());
    return new InstitutionPoints(
        institutionId, points, sector, rboInstitution, List.of(creatorAffiliationPoints));
  }

  private Candidate setupCandidateWithUnverifiedCreator(URI institutionId, String creatorName) {
    var unverifiedCreator =
        UnverifiedNviCreatorDto.builder()
            .withName(creatorName)
            .withAffiliations(List.of(institutionId))
            .build();
    var request = randomUpsertRequestBuilder().withNviCreators(unverifiedCreator).build();
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
  }

  private Candidate setupCandidateWithHandles(Set<URI> handles) {
    var request = randomUpsertRequestBuilder().build();
    var modifiedDetails =
        new PublicationDetailsDtoBuilder(request.publicationDetails()).withHandles(handles).build();
    var modifiedRequest =
        UpsertRequestBuilder.fromRequest(request).withPublicationDetails(modifiedDetails).build();
    candidateService.upsertCandidate(modifiedRequest);
    return candidateService.getCandidateByPublicationId(modifiedRequest.publicationId());
  }

  private Candidate setupCandidateWithCreator(
      NviCreatorDto creator, Organization topLevelOrganization) {
    var request =
        randomUpsertRequestBuilder()
            .withNviCreators(creator)
            .withTopLevelOrganizations(topLevelOrganization)
            .build();
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
  }

  /**
   * Simulates a candidate persisted with an incomplete organization hierarchy: the creator's
   * affiliation has no matching entry in the candidate's top-level organization trees.
   */
  private Candidate setupCandidateWithoutOrganizationTree(NviCreatorDto creator) {
    var request = randomUpsertRequestBuilder().withNviCreators(creator).build();
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
  }

  private static ContributorDto publicationContributor(
      URI contributorId, String name, Organization affiliation) {
    return ContributorDto.builder()
        .withId(contributorId)
        .withName(name)
        .withRole(ContributorRole.CREATOR)
        .withAffiliations(List.of(affiliation))
        .build();
  }

  private static PublicationDto publicationDtoWithContributors(
      Candidate candidate, List<ContributorDto> contributors) {
    return PublicationDto.builder()
        .withId(candidate.getPublicationId())
        .withStatus(PUBLISHED)
        .withContributors(contributors)
        .build();
  }

  private static PublicationDto publicationDtoWithChannel(
      Candidate candidate, PublicationChannelDto channelDto) {
    return PublicationDto.builder()
        .withId(candidate.getPublicationId())
        .withStatus(PUBLISHED)
        .withPublicationChannels(List.of(channelDto))
        .build();
  }

  private static PublicationDto publicationDtoWithTopLevelOrganizations(
      Candidate candidate, Organization... topLevelOrganizations) {
    return PublicationDto.builder()
        .withId(candidate.getPublicationId())
        .withStatus(PUBLISHED)
        .withTopLevelOrganizations(List.of(topLevelOrganizations))
        .build();
  }

  private static PublicationDto publicationDtoWithType(
      Candidate candidate, InstanceType publicationType) {
    return PublicationDto.builder()
        .withId(candidate.getPublicationId())
        .withStatus(PUBLISHED)
        .withPublicationType(publicationType)
        .build();
  }

  private static PublicationChannelDto publicationChannel(String name, ChannelType channelType) {
    return PublicationChannelDto.builder()
        .withId(randomUri())
        .withChannelType(channelType)
        .withScientificValue(ScientificValue.LEVEL_ONE)
        .withName(name)
        .build();
  }

  private Candidate candidateWithChannelTypeButNoId(ChannelType channelType) {
    return candidateWithChannel(null, channelType);
  }

  private Candidate reportedCandidateWithType(InstanceType publicationType) {
    var institutionId = randomOrganizationId();
    var publicationDetails = randomPublicationBuilder(institutionId).build();
    var pointCalculation =
        randomPointCalculationBuilder(randomOrganizationId(), institutionId)
            .instanceType(publicationType.getValue())
            .build();
    var dbCandidate =
        randomCandidateBuilder(institutionId, publicationDetails, pointCalculation)
            .reportStatus(ReportStatus.REPORTED)
            .reportedDate(Instant.now())
            .build();
    var dao = createCandidateDao(dbCandidate);
    var approvals = List.of(randomApprovalDao(dao.identifier(), institutionId));
    candidateRepository.create(dao, approvals);
    return candidateService.getCandidateByIdentifier(dao.identifier());
  }

  private Candidate candidateWithChannel(URI channelId, ChannelType channelType) {
    var institutionId = randomOrganizationId();
    var channelTypeValue = nonNull(channelType) ? channelType.getValue() : null;
    var channel =
        new DbPublicationChannel(channelId, channelTypeValue, ScientificValue.LEVEL_ONE.getValue());
    var publicationDetails = randomPublicationBuilder(institutionId).build();
    var pointCalculation =
        randomPointCalculationBuilder(randomOrganizationId(), institutionId)
            .publicationChannel(channel)
            .build();
    var dao =
        createCandidateDao(
            randomCandidateBuilder(institutionId, publicationDetails, pointCalculation).build());
    var approvals = List.of(randomApprovalDao(dao.identifier(), institutionId));
    candidateRepository.create(dao, approvals);
    return candidateService.getCandidateByIdentifier(dao.identifier());
  }
}
