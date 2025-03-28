package no.sikt.nva.nvi.common.utils;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.initializeTestDatabase;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createNumberOfCandidatesForYear;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.getYearIndexStartMarker;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.setupReportedCandidate;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.sortByIdentifier;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomIntBetween;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

class BatchScanUtilTest {

  private static final int DEFAULT_PAGE_SIZE = 700;
  private static final int SECOND_ROW = 1;
  private DynamoDbClient localDynamo;
  private BatchScanUtil batchScanUtil;
  private CandidateRepositoryHelper candidateRepository;

  @BeforeEach
  void setup() {
    localDynamo = initializeTestDatabase();
    candidateRepository = new CandidateRepositoryHelper(localDynamo);
    batchScanUtil = new BatchScanUtil(candidateRepository);
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
    assertThat(results.size(), is(equalTo(1)));
    assertEquals(secondCandidateInIndex, results.get(0));
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
    assertThat(expectedCandidates, containsInAnyOrder(results.toArray()));
  }

  @Test
  void shouldFetchCandidatesByGivenYearWithDefaultPageSizeAndStartMarkerIfNotSet() {
    var year = randomYear();
    int numberOfCandidates = DEFAULT_PAGE_SIZE + randomIntBetween(1, 10);
    var candidates = createNumberOfCandidatesForYear(year, numberOfCandidates, candidateRepository);
    var expectedCandidates = sortByIdentifier(candidates, DEFAULT_PAGE_SIZE);
    var results = batchScanUtil.fetchCandidatesByYear(year, true, null, null).getDatabaseEntries();
    assertThat(results.size(), is(equalTo(DEFAULT_PAGE_SIZE)));
    assertThat(expectedCandidates, containsInAnyOrder(results.toArray()));
  }

  @Test
  void shouldNotFetchReportedCandidatesWhenIncludeReportedCandidatesIsFalse() {
    var year = randomYear();
    var candidates = createNumberOfCandidatesForYear(year, 2, candidateRepository);
    var reportedCandidate = setupReportedCandidate(candidateRepository, year);
    var expectedCandidates = sortByIdentifier(candidates, null);
    var results = batchScanUtil.fetchCandidatesByYear(year, false, null, null).getDatabaseEntries();
    assertThat(results.size(), is(equalTo(2)));
    assertThat(expectedCandidates, containsInAnyOrder(results.toArray()));
    assertThat(results, not(containsInAnyOrder(reportedCandidate)));
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
        candidateRepository.findDaoById(getIdentifier(candidates, 0)),
        candidateRepository.findDaoById(getIdentifier(candidates, 1)));
  }

  private List<Map<String, AttributeValue>> getCandidatesInOrder() {
    return localDynamo
        .scan(ScanRequest.builder().tableName(ApplicationConstants.NVI_TABLE_NAME).build())
        .items()
        .stream()
        .filter(attributeValueMap -> CandidateDao.TYPE.equals(attributeValueMap.get("type").s()))
        .toList();
  }

  public static class CandidateRepositoryHelper extends CandidateRepository {

    public CandidateRepositoryHelper(DynamoDbClient client) {
      super(client);
    }

    public CandidateDao findDaoById(UUID id) {
      return Optional.of(CandidateDao.builder().identifier(id).build())
          .map(candidateTable::getItem)
          .orElseThrow();
    }
  }
}
