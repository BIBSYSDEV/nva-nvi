package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.createNumberOfCandidatesForYear;
import static no.sikt.nva.nvi.test.TestUtils.getYearIndexStartMarker;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.sikt.nva.nvi.test.TestUtils.sortByIdentifier;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.utils.ApplicationConstants;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

public class NviServiceTest extends LocalDynamoTest {

    public static final int YEAR = ZonedDateTime.now().getYear();
    private static final int FIRST_ROW = 0;
    private static final int SECOND_ROW = 1;
    private NviService nviService;
    private CandidateRepositoryHelper candidateRepository;

    @Test
    public void refreshVersionShouldContinue() {
        IntStream.range(0, 3).forEach(i -> candidateRepository.create(randomCandidate(), List.of()));

        var result = nviService.refresh(1, null);
        assertThat(result.shouldContinueScan(), is(equalTo(true)));
    }

    @Test
    public void shouldWriteVersionOnRefreshWithStartMarker() {
        IntStream.range(0, 2).forEach(i -> candidateRepository.create(randomCandidate(), List.of()));

        var candidates = getCandidatesInOrder();

        var originalRows = getCandidateDaos(candidates);

        nviService.refresh(1000, getStartMarker(originalRows.get(FIRST_ROW)));

        var modifiedRows = getCandidateDaos(candidates);

        assertThat(modifiedRows.get(FIRST_ROW).version(), is(equalTo(originalRows.get(FIRST_ROW).version())));
        assertThat(modifiedRows.get(SECOND_ROW).version(), is(not(equalTo(originalRows.get(SECOND_ROW).version()))));
    }

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepositoryHelper(localDynamo);
        nviService = TestUtils.nviServiceReturningOpenPeriod(localDynamo, YEAR);
    }

    @Test
    void shouldWriteVersionOnRefreshWhenStartMarkerIsNotSet() {
        var originalCandidate = randomCandidate();
        var candidate = candidateRepository.create(originalCandidate, List.of());
        var original = candidateRepository.findCandidateById(candidate.identifier()).orElseThrow();
        var result = nviService.refresh(10, null);
        var modified = candidateRepository.findCandidateById(candidate.identifier()).orElseThrow();
        assertThat(modified.version(), is(not(equalTo(original.version()))));
        assertThat(result.getStartMarker().size(), is(equalTo(0)));
        assertThat(result.getTotalItemCount(), is(equalTo(1)));
        assertThat(result.shouldContinueScan(), is(equalTo(false)));
    }

    @Test
    void shouldCreateNviPeriod() {
        var period = createPeriod("2050");
        var nviService = new NviService(localDynamo);
        nviService.createPeriod(period);
        assertThat(nviService.getPeriod(period.publishingYear()), is(equalTo(period)));
    }

    @Test
    void shouldThrowIllegalArgumentWhenPeriodMissedMandatoryValues() {
        var period = new DbNviPeriod(String.valueOf(ZonedDateTime.now().plusYears(1).getYear()), null,
                                     ZonedDateTime.now().plusMonths(10).toInstant(), new Username(randomString()),
                                     null);
        var nviService = new NviService(localDynamo);

        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldUpdateNviPeriod() {
        var originalPeriod = createPeriod(String.valueOf(ZonedDateTime.now().getYear()));
        nviService.createPeriod(originalPeriod);
        nviService.updatePeriod(
            originalPeriod.copy().reportingDate(originalPeriod.reportingDate().plusSeconds(500)).build());
        var fetchedPeriod = nviService.getPeriod(originalPeriod.publishingYear());
        assertThat(fetchedPeriod, is(not(equalTo(originalPeriod))));
    }

    @Test
    void shouldNotAllowNviPeriodReportingDateInInPast() {
        var originalPeriod = createPeriod(String.valueOf(ZonedDateTime.now().getYear()));
        nviService.createPeriod(originalPeriod);
        var updatedPeriod = originalPeriod.copy().reportingDate(ZonedDateTime.now().minusWeeks(10).toInstant()).build();

        assertThrows(IllegalArgumentException.class, () -> nviService.updatePeriod(updatedPeriod));
    }

    @Test
    void shouldNotAllowNviPeriodStartAfterReportingDate() {
        var originalPeriod = createPeriod(String.valueOf(ZonedDateTime.now().getYear()));
        nviService.createPeriod(originalPeriod);
        var updatedPeriod = originalPeriod.copy().startDate(ZonedDateTime.now().plusYears(1).toInstant()).build();
        assertThrows(IllegalArgumentException.class, () -> nviService.updatePeriod(updatedPeriod));
    }

    @Test
    void shouldReturnIllegalArgumentExceptionWhenPublishingYearIsNotAYear() {
        var period = createPeriod("20AA");
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentExceptionWhenPublishingYearHasInvalidLength() {
        var period = createPeriod("22");
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentExceptionWhenWhenStartDateHasAlreadyBeenReached() {
        var period = DbNviPeriod.builder()
                         .startDate(ZonedDateTime.now().minusDays(1).toInstant())
                         .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant())
                         .publishingYear(String.valueOf(ZonedDateTime.now().getYear()))
                         .createdBy(randomUsername())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentWhenReportingDateIsBeforeNow() {
        var period = DbNviPeriod.builder()
                         .reportingDate(Instant.MIN)
                         .startDate(ZonedDateTime.now().plusMonths(1).toInstant())
                         .publishingYear("2023")
                         .createdBy(Username.builder().value("me").build())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentWhenStartDateIsAfterReportingDate() {
        var period = DbNviPeriod.builder()
                         .startDate(ZonedDateTime.now().plusMonths(10).toInstant())
                         .reportingDate(ZonedDateTime.now().plusMonths(1).toInstant())
                         .publishingYear(String.valueOf(ZonedDateTime.now().getYear()))
                         .createdBy(Username.builder().value("me").build())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentWhenPublishingYearIsNotAValidYear() {
        var period = DbNviPeriod.builder()
                         .reportingDate(Instant.MIN)
                         .publishingYear("now!")
                         .createdBy(Username.builder().value("me").build())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnPeriodsOnlyWhenFetchingPeriods() {
        var nviService = new NviService(localDynamo);
        nviService.createPeriod(createPeriod(String.valueOf(ZonedDateTime.now().getYear())));
        nviService.createPeriod(createPeriod(String.valueOf(ZonedDateTime.now().plusYears(1).getYear())));
        var periods = nviService.getPeriods();
        assertThat(periods, hasSize(2));
    }

    @Test
    void shouldFetchCandidatesByGivenYearAndStartMarker() {
        var year = randomYear();
        var candidates = createNumberOfCandidatesForYear(year, 2, candidateRepository);
        var expectedCandidates = sortByIdentifier(candidates, null);
        var firstCandidateInIndex = expectedCandidates.get(0);
        var secondCandidateInIndex = expectedCandidates.get(1);
        var startMarker = getYearIndexStartMarker(firstCandidateInIndex);
        var results = nviService.fetchCandidatesByYear(year, null, startMarker).getCandidates();
        assertThat(results.size(), is(equalTo(1)));
        assertEquals(secondCandidateInIndex, results.get(0));
    }

    @Test
    void shouldFetchCandidatesByGivenYearAndPageSize() {
        var searchYear = randomYear();
        var candidates = createNumberOfCandidatesForYear(searchYear, 10, candidateRepository);
        createNumberOfCandidatesForYear(randomYear(), 10, candidateRepository);
        int pageSize = 5;
        var expectedCandidates = sortByIdentifier(candidates, pageSize);
        var results = nviService.fetchCandidatesByYear(searchYear, pageSize, null).getCandidates();
        assertThat(results.size(), is(equalTo(pageSize)));
        assertThat(expectedCandidates, containsInAnyOrder(results.toArray()));
    }

    private static Map<String, String> getStartMarker(CandidateDao dao) {
        return getStartMarker(dao.primaryKeyHashKey(),
                              dao.primaryKeyHashKey());
    }

    private static Map<String, String> getStartMarker(String primaryKeyHashKey, String primaryKeyRangeKey) {
        return Map.of("PrimaryKeyRangeKey", primaryKeyHashKey, "PrimaryKeyHashKey",
                      primaryKeyRangeKey);
    }

    private static UUID getIdentifier(List<Map<String, AttributeValue>> candidates, int index) {
        return UUID.fromString(candidates.get(index).get("identifier").s());
    }

    private static DbNviPeriod createPeriod(String publishingYear) {
        return DbNviPeriod.builder()
                   .startDate(ZonedDateTime.now().plusMonths(1).toInstant())
                   .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant())
                   .publishingYear(publishingYear)
                   .createdBy(randomUsername())
                   .build();
    }

    private static Username randomUsername() {
        return Username.fromString(randomString());
    }

    private List<CandidateDao> getCandidateDaos(List<Map<String, AttributeValue>> candidates) {
        return Arrays.asList(candidateRepository.findDaoById(getIdentifier(candidates, 0)),
                             candidateRepository.findDaoById(getIdentifier(candidates, 1)));
    }

    private List<Map<String, AttributeValue>> getCandidatesInOrder() {
        return localDynamo.scan(ScanRequest.builder().tableName(ApplicationConstants.NVI_TABLE_NAME).build())
                   .items()
                   .stream()
                   .filter(a -> a.get("type").s().equals("CANDIDATE"))
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