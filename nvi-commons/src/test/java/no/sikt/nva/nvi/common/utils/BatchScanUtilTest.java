package no.sikt.nva.nvi.common.utils;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.BATCH_SCAN_RECOVERY_QUEUE;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getEventBasedBatchScanHandlerEnvironment;
import static no.sikt.nva.nvi.common.SampleExpandedPublicationFactory.defaultExpandedPublicationFactory;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateInRepository;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createNumberOfCandidatesForYear;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.getYearIndexStartMarker;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.setupReportedCandidate;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.sortByIdentifier;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidate;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationDetailsFixtures.randomPublicationBuilder;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.mapToContributorDto;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.randomCreator;
import static no.sikt.nva.nvi.common.model.NviCreatorFixtures.mapToDbCreators;
import static no.sikt.nva.nvi.common.model.NviCreatorFixtures.unverifiedNviCreatorFrom;
import static no.sikt.nva.nvi.common.model.NviCreatorFixtures.verifiedNviCreatorFrom;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static no.sikt.nva.nvi.test.TestUtils.randomIntBetween;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.StringUtils.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.model.PublicationDetails;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

class BatchScanUtilTest {

  private static final int DEFAULT_PAGE_SIZE = 100;
  private static final int SECOND_ROW = 1;
  private BatchScanUtil batchScanUtil;
  private CandidateRepository candidateRepository;
  private TestScenario scenario;
  private FakeSqsClient queueClient;

  @BeforeEach
  void setup() {
    scenario = new TestScenario();
    candidateRepository = scenario.getCandidateRepository();

    queueClient = new FakeSqsClient();
    batchScanUtil =
        new BatchScanUtil(
            scenario.getCandidateRepository(),
            scenario.getS3StorageReaderForExpandedResourcesBucket(),
            queueClient,
            getEventBasedBatchScanHandlerEnvironment());
  }

  @Test
  void shouldReturnTrueWhenThereAreMoreItemsToScan() {
    createNumberOfCandidatesForYear(randomYear(), 2, scenario);

    var result = batchScanUtil.migrateAndUpdateVersion(1, null, emptyList());
    assertThat(result.shouldContinueScan(), is(equalTo(true)));
  }

  @Test
  void shouldWriteVersionOnRefreshWithStartMarker() {
    createNumberOfCandidatesForYear(randomYear(), 2, scenario);
    var candidates = getCandidatesInOrder();
    var originalRows = getCandidates(candidates);
    batchScanUtil.migrateAndUpdateVersion(
        1000, getStartMarker(originalRows.getFirst()), emptyList());
    var modifiedRows = getCandidates(candidates);

    assertThat(modifiedRows.getFirst().version(), is(equalTo(originalRows.getFirst().version())));
    assertThat(
        modifiedRows.get(SECOND_ROW).version(),
        is(not(equalTo(originalRows.get(SECOND_ROW).version()))));
  }

  @Test
  void shouldWriteVersionOnRefreshWhenStartMarkerIsNotSet() {
    var originalCandidate = randomCandidate();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, originalCandidate);
    var original = candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();
    var result = batchScanUtil.migrateAndUpdateVersion(10, null, emptyList());
    var modified = candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();
    assertThat(modified.version(), is(not(equalTo(original.version()))));
    assertThat(result.getStartMarker().size(), is(equalTo(0)));
    assertThat(result.getTotalItemCount(), is(equalTo(1)));
    assertThat(result.shouldContinueScan(), is(equalTo(false)));
  }

  @Test
  void shouldFetchCandidatesByGivenYearAndStartMarker() {
    var year = randomYear();
    var candidates = createNumberOfCandidatesForYear(year, 2, scenario);
    var expectedCandidates = sortByIdentifier(candidates, null);
    var firstCandidateInIndex = expectedCandidates.get(0);
    var secondCandidateInIndex = expectedCandidates.get(1);
    var startMarker = getYearIndexStartMarker(firstCandidateInIndex);
    var results =
        batchScanUtil.fetchCandidatesByYear(year, true, null, startMarker).getDatabaseEntries();
    Assertions.assertThat(results)
        .extracting(CandidateDao::identifier)
        .containsOnly(secondCandidateInIndex.identifier());
  }

  @Test
  void shouldFetchCandidatesByGivenYearAndPageSize() {
    var searchYear = "2023";
    var candidates = createNumberOfCandidatesForYear(searchYear, 10, scenario);
    createNumberOfCandidatesForYear("2022", 10, scenario);
    int pageSize = 5;
    var expectedCandidates = sortByIdentifier(candidates, pageSize);
    var expectedIdentifiers = expectedCandidates.stream().map(CandidateDao::identifier).toList();
    var results =
        batchScanUtil.fetchCandidatesByYear(searchYear, true, pageSize, null).getDatabaseEntries();
    assertThat(results.size(), is(equalTo(pageSize)));
    Assertions.assertThat(results)
        .extracting(CandidateDao::identifier)
        .containsExactlyInAnyOrderElementsOf(expectedIdentifiers);
  }

  @Test
  void shouldFetchCandidatesByGivenYearWithDefaultPageSizeAndStartMarkerIfNotSet() {
    var year = randomYear();
    int numberOfCandidates = DEFAULT_PAGE_SIZE + randomIntBetween(1, 10);
    var candidates = createNumberOfCandidatesForYear(year, numberOfCandidates, scenario);
    var expectedCandidates = sortByIdentifier(candidates, DEFAULT_PAGE_SIZE);
    var expectedIdentifiers = expectedCandidates.stream().map(CandidateDao::identifier).toList();
    var results = batchScanUtil.fetchCandidatesByYear(year, true, null, null).getDatabaseEntries();
    assertThat(results.size(), is(equalTo(DEFAULT_PAGE_SIZE)));
    Assertions.assertThat(results)
        .extracting(CandidateDao::identifier)
        .containsExactlyInAnyOrderElementsOf(expectedIdentifiers);
  }

  @Test
  void shouldNotFetchReportedCandidatesWhenIncludeReportedCandidatesIsFalse() {
    var year = randomYear();
    var candidates = createNumberOfCandidatesForYear(year, 2, scenario);
    var expectedCandidates = candidates.stream().map(CandidateDao::identifier).toList();
    var reportedCandidate = setupReportedCandidate(candidateRepository, year);
    var results = batchScanUtil.fetchCandidatesByYear(year, false, null, null).getDatabaseEntries();
    Assertions.assertThat(results)
        .extracting(CandidateDao::identifier)
        .containsExactlyInAnyOrderElementsOf(expectedCandidates)
        .doesNotContain(reportedCandidate.identifier());
  }

  /**
   * @deprecated Temporary migration code. To be removed when all candidates have been migrated.
   */
  @Deprecated(forRemoval = true, since = "2025-04-29")
  @Test
  void shouldMigratePublicationIdentifierField() {
    var publication = defaultExpandedPublicationFactory(scenario).getExpandedPublication();
    var dbCandidate =
        setupRandomCandidateBuilderWithPublicationInS3(publication)
            .publicationIdentifier(null)
            .publicationDetails(null)
            .pointCalculation(null)
            .build();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, dbCandidate);

    batchScanUtil.migrateAndUpdateVersion(10, null, emptyList());
    var updatedDao = candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();
    var actualPublicationIdentifier = updatedDao.candidate().publicationIdentifier();

    assertNull(dbCandidate.publicationIdentifier());
    Assertions.assertThat(dbCandidate.publicationId().toString())
        .contains(actualPublicationIdentifier);
  }

  /**
   * @deprecated Temporary migration code. To be removed when all candidates have been migrated.
   */
  @Deprecated(forRemoval = true, since = "2025-04-29")
  @Test
  void shouldMigrateCreatorNames() {
    var organization = scenario.setupTopLevelOrganizationWithSubUnits();
    var originalCreatorDto = randomCreator(organization).withName(randomString()).build();
    var originalCreator = new DbCreator(originalCreatorDto.id(), null, List.of(organization.id()));

    var publicationBuilder =
        new SampleExpandedPublicationFactory(scenario)
            .withTopLevelOrganizations(List.of(organization));
    var publication = publicationBuilder.getExpandedPublication();

    var dbCandidate =
        setupRandomCandidateBuilderWithPublicationInS3(publication)
            .publicationDetails(null)
            .pointCalculation(null)
            .creators(List.of(originalCreator))
            .build();

    var candidateIdentifier = createCandidateInRepository(candidateRepository, dbCandidate);
    var originalCandidate =
        candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();
    Assertions.assertThat(originalCandidate.candidate().creators())
        .allMatch(creator -> isBlank(creator.creatorName()));

    var updatedPublication =
        publicationBuilder.withContributor(originalCreatorDto).getExpandedPublication();
    scenario.setupExpandedPublicationInS3(updatedPublication);

    batchScanUtil.migrateAndUpdateVersion(10, null, emptyList());
    var updatedDao = candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();
    var actualCreators = updatedDao.candidate().creators();

    Assertions.assertThat(actualCreators)
        .extracting("creatorName")
        .containsOnlyOnce(originalCreatorDto.name());
  }

  /**
   * @deprecated Temporary migration code. To be removed when all candidates have been migrated.
   */
  @Deprecated(forRemoval = true, since = "2025-05-15")
  @Test
  void shouldMigrateTopLevelNviOrganizations() {
    // Given an applicable Candidate with an expanded publication in S3
    // And the Candidate does not have the field topLevelNviOrganizations persisted in the database
    // When the migration is run
    // Then the persisted Candidate should have the topLevelNviOrganizations field populated
    // And the topLevelNviOrganizations field only includes NVI organizations with affiliated
    // Creators

    // Create a publication with a mix of different organizations and creators
    var publicationBuilder = new SampleExpandedPublicationFactory(scenario);
    var nviOrganization1 = publicationBuilder.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    var nviOrganization2 = publicationBuilder.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    var nviOrganization3 = publicationBuilder.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    var nonNviOrganization =
        publicationBuilder.setupTopLevelOrganization(COUNTRY_CODE_SWEDEN, false);

    var verifiedCreator = verifiedNviCreatorFrom(nviOrganization1, nviOrganization1.id());
    var unverifiedCreator =
        unverifiedNviCreatorFrom(nviOrganization2, nviOrganization2.hasPart().getFirst().id());
    var expectedNviCreators = List.of(verifiedCreator, unverifiedCreator);
    var expectedTopLevelOrganizations = List.of(nviOrganization1, nviOrganization2);
    var expectedLanguage = "http://lexvo.org/id/iso639-3/nob";
    var publication =
        publicationBuilder
            .withContributor(mapToContributorDto(verifiedCreator))
            .withContributor(mapToContributorDto(unverifiedCreator))
            .withNonCreatorAffiliatedWith(nviOrganization3)
            .withCreatorAffiliatedWith(nonNviOrganization)
            .getExpandedPublicationBuilder()
            .withLanguage(expectedLanguage)
            .build();

    // Set up an existing Candidate in the database with identifiers matching the publication
    var dbCreators = mapToDbCreators(expectedNviCreators);
    var dbPublicationDetails =
        randomPublicationBuilder(randomUri())
            .topLevelNviOrganizations(null)
            .creators(dbCreators)
            .build();
    var originalDbCandidate =
        setupRandomCandidateBuilderWithPublicationInS3(publication)
            .publicationDetails(dbPublicationDetails)
            .creators(dbCreators)
            .build();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, originalDbCandidate);
    var originalCandidate =
        candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();
    Assertions.assertThat(
            originalCandidate.candidate().publicationDetails().topLevelNviOrganizations())
        .isEmpty();

    // Run the migration and check that the topLevelNviOrganizations field is populated as expected
    batchScanUtil.migrateAndUpdateVersion(10, null, emptyList());
    var actualCandidate = scenario.getCandidateByPublicationId(publication.id());

    Assertions.assertThat(actualCandidate.publicationDetails())
        .extracting(
            PublicationDetails::language,
            PublicationDetails::topLevelOrganizations,
            PublicationDetails::nviCreators)
        .containsExactly(expectedLanguage, expectedTopLevelOrganizations, expectedNviCreators);
  }

  /**
   * @deprecated Temporary migration code. To be removed when all candidates have been migrated.
   */
  @Deprecated(forRemoval = true, since = "2025-05-15")
  @Test
  void shouldNotDeleteUnrelatedDataWhenMigratingTopLevelOrganizations() {
    // Create an expanded publication document
    var publicationBuilder = new SampleExpandedPublicationFactory(scenario);
    var nviOrganization1 = publicationBuilder.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    var verifiedCreator = verifiedNviCreatorFrom(nviOrganization1, nviOrganization1.id());
    var expectedNviCreators = List.of(verifiedCreator);
    var expectedTopLevelOrganizations = List.of(nviOrganization1);
    var expectedModifiedDate = Instant.now();
    var publication =
        publicationBuilder
            .withContributor(mapToContributorDto(verifiedCreator))
            .getExpandedPublicationBuilder()
            .withModifiedDate(expectedModifiedDate.toString())
            .build();

    // Set up an existing Candidate in the database with identifiers matching the publication
    var dbCreators = mapToDbCreators(expectedNviCreators);
    var dbPublicationDetails =
        randomPublicationBuilder(randomUri())
            .topLevelNviOrganizations(null)
            .abstractText(randomString())
            .contributorCount(randomInteger())
            .modifiedDate(expectedModifiedDate)
            .creators(dbCreators)
            .build();
    var originalDbCandidate =
        setupRandomCandidateBuilderWithPublicationInS3(publication)
            .publicationDetails(dbPublicationDetails)
            .creators(dbCreators)
            .build();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, originalDbCandidate);
    var originalCandidate =
        candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();
    Assertions.assertThat(
            originalCandidate.candidate().publicationDetails().topLevelNviOrganizations())
        .isEmpty();

    // Run the migration and check that persisted data is updated correctly
    batchScanUtil.migrateAndUpdateVersion(10, null, emptyList());
    var actualCandidate = scenario.getCandidateByPublicationId(publication.id());

    Assertions.assertThat(actualCandidate.publicationDetails())
        .extracting(
            PublicationDetails::topLevelOrganizations,
            PublicationDetails::nviCreators,
            PublicationDetails::abstractText,
            PublicationDetails::modifiedDate)
        .containsExactly(
            expectedTopLevelOrganizations,
            expectedNviCreators,
            dbPublicationDetails.abstractText(),
            dbPublicationDetails.modifiedDate());
  }

  @Test
  void shouldNotFailForWholeBatchWhenSingleRecordFails() {
    createNumberOfCandidatesForYear(randomYear(), 2, scenario);

    var details =
        randomPublicationBuilder(randomUri())
            .topLevelNviOrganizations(null)
            .abstractText(randomString())
            .contributorCount(randomInteger())
            .modifiedDate(Instant.now())
            .creators(List.of())
            .build();
    var candidate = randomCandidateBuilder(randomUri(), details).build();
    createCandidateInRepository(candidateRepository, candidate);

    assertDoesNotThrow(() -> batchScanUtil.migrateAndUpdateVersion(3, null, emptyList()));
  }

  @Test
  void shouldSendFailingRecordToRecoveryQueue() {
    createNumberOfCandidatesForYear(randomYear(), 2, scenario);

    var details =
        randomPublicationBuilder(randomUri())
            .topLevelNviOrganizations(null)
            .abstractText(randomString())
            .contributorCount(randomInteger())
            .modifiedDate(Instant.now())
            .creators(List.of())
            .build();
    var candidateFailingUnderBatchScan = randomCandidateBuilder(randomUri(), details).build();
    var candidateIdentifier =
        createCandidateInRepository(candidateRepository, candidateFailingUnderBatchScan);
    var candidate = candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();

    batchScanUtil.migrateAndUpdateVersion(10, null, emptyList());

    var sqsMessage =
        queueClient.getAllSentSqsEvents(BATCH_SCAN_RECOVERY_QUEUE.getValue()).getFirst();

    assertEquals(
        candidate.identifier().toString(),
        sqsMessage.getMessageAttributes().get("candidateIdentifier").getStringValue());
  }

  private static Map<String, String> getStartMarker(CandidateDao dao) {
    return getStartMarker(dao.primaryKeyHashKey(), dao.primaryKeyHashKey());
  }

  private static Map<String, String> getStartMarker(
      String primaryKeyHashKey, String primaryKeyRangeKey) {
    return Map.of("PrimaryKeyRangeKey", primaryKeyHashKey, "PrimaryKeyHashKey", primaryKeyRangeKey);
  }

  private static UUID getIdentifier(List<Map<String, AttributeValue>> candidates, int index) {
    return UUID.fromString(candidates.get(index).get("identifier").s());
  }

  private List<CandidateDao> getCandidates(List<Map<String, AttributeValue>> candidates) {
    return Arrays.asList(
        candidateRepository.findCandidateById(getIdentifier(candidates, 0)).orElseThrow(),
        candidateRepository.findCandidateById(getIdentifier(candidates, 1)).orElseThrow());
  }

  private List<Map<String, AttributeValue>> getCandidatesInOrder() {
    return scenario
        .getLocalDynamo()
        .scan(ScanRequest.builder().tableName(ApplicationConstants.NVI_TABLE_NAME).build())
        .items()
        .stream()
        .filter(attributeValueMap -> CandidateDao.TYPE.equals(attributeValueMap.get("type").s()))
        .toList();
  }

  private DbCandidate.Builder setupRandomCandidateBuilderWithPublicationInS3(
      SampleExpandedPublication publication) {
    var publicationBucketUri = scenario.setupExpandedPublicationInS3(publication);
    return randomCandidateBuilder(true)
        .publicationId(publication.id())
        .publicationBucketUri(publicationBucketUri);
  }
}
