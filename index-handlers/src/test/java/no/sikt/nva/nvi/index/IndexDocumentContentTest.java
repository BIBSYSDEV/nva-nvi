package no.sikt.nva.nvi.index;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateDao;
import static no.sikt.nva.nvi.common.db.DbApprovalStatusFixtures.randomApprovalDao;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.DbPointCalculationFixtures.randomPointCalculationBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationDetailsFixtures.randomPublicationBuilder;
import static no.sikt.nva.nvi.common.model.NviCreatorFixtures.randomUnverifiedNviCreator;
import static no.sikt.nva.nvi.common.model.NviCreatorFixtures.randomVerifiedNviCreator;
import static no.sikt.nva.nvi.common.model.NviCreatorFixtures.verifiedNviCreatorFrom;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.createOrganizationHierarchy;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.organizationNode;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganization;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomTopLevelOrganization;
import static no.sikt.nva.nvi.test.TestConstants.PUBLISHED;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomName;
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
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.model.Sector;
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
import org.junit.jupiter.params.provider.NullSource;
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
    var institution = randomTopLevelOrganization();
    var candidate = setupCandidateWithInstitutionPoints(institution, sector, false);
    stubPublication(candidate);

    var document = generateIndexDocument(candidate);

    assertThat(approvalFor(document, institution).sector()).isEqualTo(sector.toString());
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(value = Sector.class, names = "UNKNOWN", mode = EnumSource.Mode.INCLUDE)
  void shouldNotPopulateSectorInApprovalViewWhenSectorIsUnknown(Sector sector) {
    var institution = randomTopLevelOrganization();
    var candidate = setupCandidateWithInstitutionPoints(institution, sector, false);
    stubPublication(candidate);

    var document = generateIndexDocument(candidate);

    assertThat(approvalFor(document, institution).sector()).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldPopulateRboInstitutionInIndexDocumentApprovalView(boolean rboInstitution) {
    var institution = randomTopLevelOrganization();
    var candidate = setupCandidateWithInstitutionPoints(institution, Sector.UHI, rboInstitution);
    stubPublication(candidate);

    var document = generateIndexDocument(candidate);

    assertThat(approvalFor(document, institution).rboInstitution()).isEqualTo(rboInstitution);
  }

  @Test
  void shouldUsePublicationLabelsWhenCandidateHasNoLabels() {
    var institution = randomOrganization().withLabels(null).build();
    var candidate = setupCandidateWithInstitutionPoints(institution, Sector.UHI, false);
    var expectedLabels = Map.of("nb", randomString(), "en", randomString());
    var labeledInstitution =
        Organization.builder().withId(institution.id()).withLabels(expectedLabels).build();
    stubPublication(
        candidate, publicationDtoWithTopLevelOrganizations(candidate, labeledInstitution));

    var document = generateIndexDocument(candidate);

    assertThat(approvalFor(document, institution).labels()).isEqualTo(expectedLabels);
  }

  @Test
  void shouldReturnEmptyApprovalLabelsWhenNeitherCandidateNorPublicationHasLabels() {
    var institution = randomOrganization().withLabels(null).build();
    var candidate = setupCandidateWithInstitutionPoints(institution, Sector.UHI, false);
    var unlabeledInstitution = Organization.builder().withId(institution.id()).build();
    stubPublication(
        candidate, publicationDtoWithTopLevelOrganizations(candidate, unlabeledInstitution));

    var document = generateIndexDocument(candidate);

    assertThat(approvalFor(document, institution).labels()).isEmpty();
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
    var unverifiedCreator = randomUnverifiedNviCreator();
    var expectedName = unverifiedCreator.name();
    var candidate = setupCandidateWithUnverifiedCreator(unverifiedCreator);
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

  /**
   * Pins the contract documented on {@code CandidateToIndexDocumentMapper}: an expanded publication
   * that cannot be parsed must yield a lean Candidate-only document instead of failing.
   */
  @Test
  void shouldBuildCandidateOnlyIndexDocumentWhenPublicationCannotBeParsed() {
    var creator = randomVerifiedNviCreator();
    var candidate = setupCandidateWithCreator(creator);
    stubPublicationParseFailure(candidate);

    var document = generateIndexDocument(candidate);

    assertThat(document.identifier()).isEqualTo(candidate.identifier());
    assertThat(document.publicationDetails().title())
        .isEqualTo(candidate.publicationDetails().title());
    assertThat(document.publicationDetails().contributors()).isEmpty();
    assertThat(document.publicationDetails().nviContributors())
        .extracting(NviContributor::name)
        .containsExactly(creator.name());
  }

  @Test
  void shouldTolerateContributorWithoutRolesInPublication() {
    var creator = randomVerifiedNviCreator();
    var candidate = setupCandidateWithCreator(creator);
    var contributorWithoutRoles =
        ContributorDto.builder()
            .withId(randomUri())
            .withName(randomName())
            .withAffiliations(List.copyOf(creator.topLevelNviOrganizations()))
            .build();
    stubPublication(
        candidate, publicationDtoWithContributors(candidate, List.of(contributorWithoutRoles)));

    var document = generateIndexDocument(candidate);

    var indexedContributor = document.publicationDetails().contributors().getFirst();
    assertThat(indexedContributor.name()).isEqualTo(contributorWithoutRoles.name());
    assertThat(indexedContributor.role()).isNull();
  }

  @Test
  void shouldIndexNviCreatorFromCandidateWhenRemovedFromPublication() {
    var creator = randomVerifiedNviCreator();
    var candidate = setupCandidateWithCreator(creator);
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
    var creator = verifiedNviCreatorFrom(institution, sectionId);
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
        .containsExactlyInAnyOrder(departmentId, topLevelId);
  }

  @Test
  void shouldIndexNviAffiliationAsStandaloneLeafWhenCandidateHasNoOrganizationTree() {
    var affiliation = organizationNode(randomOrganizationId(), randomOrganizationId());
    var creator = verifiedNviCreatorFrom(affiliation);
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
    var creator =
        verifiedNviCreatorFrom(institution, institution.id()).copy().withName(null).build();
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

  private static ApprovalView approvalFor(
      NviCandidateIndexDocument document, Organization institution) {
    return document.approvals().stream()
        .filter(approval -> approval.institutionId().equals(institution.id()))
        .findFirst()
        .orElseThrow();
  }

  private Candidate setupCandidateWithInstitutionPoints(
      Organization institution, Sector sector, boolean rboInstitution) {
    var verifiedCreator = verifiedNviCreatorFrom(institution);
    var institutionPoints =
        buildInstitutionPoints(institution.id(), sector, rboInstitution, randomBigDecimal());
    var request =
        randomUpsertRequestBuilder()
            .withCreatorsAndPoints(Map.of(institution, List.of(verifiedCreator)))
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

  private Candidate setupCandidateWithUnverifiedCreator(NviCreator unverifiedCreator) {
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
      NviCreator creator, Organization topLevelOrganization) {
    var request =
        randomUpsertRequestBuilder()
            .withNviCreators(creator)
            .withTopLevelOrganizations(topLevelOrganization)
            .build();
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
  }

  private Candidate setupCandidateWithCreator(NviCreator creator) {

    var request =
        randomUpsertRequestBuilder()
            .withNviCreators(creator)
            .withTopLevelOrganizations(creator.topLevelNviOrganizations())
            .build();
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
  }

  /**
   * Simulates a candidate persisted with an incomplete organization hierarchy: the creator's
   * affiliation has no matching entry in the candidate's top-level organization trees.
   */
  private Candidate setupCandidateWithoutOrganizationTree(NviCreator creator) {
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
