package no.sikt.nva.nvi.common.utils;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.SampleExpandedPublicationFactory.defaultExpandedPublicationFactory;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createNumberOfCandidatesForYear;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.getYearIndexStartMarker;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.setupReportedCandidate;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.sortByIdentifier;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidate;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.test.TestUtils.randomIntBetween;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
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
  private SampleExpandedPublicationFactory publicationFactory; // FIXME

  @BeforeEach
  void setup() {
    scenario = new TestScenario();
    candidateRepository = scenario.getCandidateRepository();

    batchScanUtil =
        new BatchScanUtil(scenario.getCandidateRepository(), scenario.getS3StorageReader());
    publicationFactory =
        defaultExpandedPublicationFactory(
            mock(AuthorizedBackendUriRetriever.class), scenario.getUriRetriever());
  }

  @Test
  void shouldReturnTrueWhenThereAreMoreItemsToScan() {
    IntStream.range(0, 3).forEach(i -> candidateRepository.create(randomCandidate(), List.of()));

    var result = batchScanUtil.migrateAndUpdateVersion(1, null, emptyList());
    assertThat(result.shouldContinueScan(), is(equalTo(true)));
  }

  @Test
  void shouldWriteVersionOnRefreshWithStartMarker() {
    IntStream.range(0, 2).forEach(i -> candidateRepository.create(randomCandidate(), List.of()));
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
    var candidate = candidateRepository.create(originalCandidate, List.of());
    var original = candidateRepository.findCandidateById(candidate.identifier()).orElseThrow();
    var result = batchScanUtil.migrateAndUpdateVersion(10, null, emptyList());
    var modified = candidateRepository.findCandidateById(candidate.identifier()).orElseThrow();
    assertThat(modified.version(), is(not(equalTo(original.version()))));
    assertThat(result.getStartMarker().size(), is(equalTo(0)));
    assertThat(result.getTotalItemCount(), is(equalTo(1)));
    assertThat(result.shouldContinueScan(), is(equalTo(false)));
  }

  @Test
  void shouldFetchCandidatesByGivenYearAndStartMarker() {
    var year = randomYear();
    var candidates = createNumberOfCandidatesForYear(year, 2, candidateRepository);
    var expectedCandidates = sortByIdentifier(candidates, null);
    var firstCandidateInIndex = expectedCandidates.get(0);
    var secondCandidateInIndex = expectedCandidates.get(1);
    var startMarker = getYearIndexStartMarker(firstCandidateInIndex);
    var results =
        batchScanUtil.fetchCandidatesByYear(year, true, null, startMarker).getDatabaseEntries();
    var expectedResults = List.of(secondCandidateInIndex);
    Assertions.assertThat(results)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedResults);
  }

  @Test
  void shouldFetchCandidatesByGivenYearAndPageSize() {
    var searchYear = "2023";
    var candidates = createNumberOfCandidatesForYear(searchYear, 10, candidateRepository);
    createNumberOfCandidatesForYear("2022", 10, candidateRepository);
    int pageSize = 5;
    var expectedCandidates = sortByIdentifier(candidates, pageSize);
    var results =
        batchScanUtil.fetchCandidatesByYear(searchYear, true, pageSize, null).getDatabaseEntries();
    assertThat(results.size(), is(equalTo(pageSize)));
    Assertions.assertThat(results)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedCandidates);
  }

  @Test
  void shouldFetchCandidatesByGivenYearWithDefaultPageSizeAndStartMarkerIfNotSet() {
    var year = randomYear();
    int numberOfCandidates = DEFAULT_PAGE_SIZE + randomIntBetween(1, 10);
    var candidates = createNumberOfCandidatesForYear(year, numberOfCandidates, candidateRepository);
    var expectedCandidates = sortByIdentifier(candidates, DEFAULT_PAGE_SIZE);
    var results = batchScanUtil.fetchCandidatesByYear(year, true, null, null).getDatabaseEntries();
    assertThat(results.size(), is(equalTo(DEFAULT_PAGE_SIZE)));
    Assertions.assertThat(results)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedCandidates);
  }

  @Test
  void shouldNotFetchReportedCandidatesWhenIncludeReportedCandidatesIsFalse() {
    var year = randomYear();
    var candidates = createNumberOfCandidatesForYear(year, 2, candidateRepository);
    var reportedCandidate = setupReportedCandidate(candidateRepository, year);
    var expectedCandidates = sortByIdentifier(candidates, null);
    var results = batchScanUtil.fetchCandidatesByYear(year, false, null, null).getDatabaseEntries();
    assertThat(results.size(), is(equalTo(2)));
    assertThat(results, not(containsInAnyOrder(reportedCandidate)));
    Assertions.assertThat(results)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedCandidates);
  }

  /**
   * @deprecated Temporary migration code. To be removed when all candidates have been migrated.
   */
  @Deprecated(forRemoval = true, since = "2025-04-29")
  @Test
  void shouldMigratePublicationIdentifierField() {
    var dbCandidate =
        setupRandomCandidateBuilderWithPublicationInS3()
            .publicationIdentifier(null)
            .publicationDetails(null)
            .build();
    var originalCandidate = candidateRepository.create(dbCandidate, List.of());

    batchScanUtil.migrateAndUpdateVersion(10, null, emptyList());
    var updatedDao =
        candidateRepository.findCandidateById(originalCandidate.identifier()).orElseThrow();
    var actualPublicationIdentifier = updatedDao.candidate().publicationIdentifier();

    assertNull(originalCandidate.candidate().publicationIdentifier());
    Assertions.assertThat(originalCandidate.candidate().publicationId().toString())
        .contains(actualPublicationIdentifier);
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

  private DbCandidate.Builder setupRandomCandidateBuilderWithPublicationInS3() {
    var publicationFactory =
        defaultExpandedPublicationFactory(
            mock(AuthorizedBackendUriRetriever.class), scenario.getUriRetriever());
    var publication = publicationFactory.getExpandedPublication();
    var publicationBucketUri = scenario.addPublicationToS3(publication);
    return randomCandidateBuilder(true)
        .publicationId(publication.id())
        .publicationBucketUri(publicationBucketUri);
  }
}
