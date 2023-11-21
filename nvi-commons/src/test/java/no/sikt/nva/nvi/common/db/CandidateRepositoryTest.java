package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.sikt.nva.nvi.test.TestUtils.randomIntBetween;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateRepositoryTest extends LocalDynamoTest {

    public static final int DEFAULT_PAGE_SIZE = 700;
    public static final String UUID_SEPERATOR = "-";
    private CandidateRepository candidateRepository;

    @BeforeEach
    public void setUp() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
    }

    @Test
    public void shouldThrowExceptionWhenAttemptingToSaveCandidateWithExistingPublicationId() {
        var publicationId = randomUri();
        var candidate1 = randomCandidateBuilder(true).publicationId(publicationId).build();
        var candidate2 = randomCandidateBuilder(true).publicationId(publicationId).build();
        candidateRepository.create(candidate1, List.of());
        assertThrows(RuntimeException.class, () -> candidateRepository.create(candidate2, List.of()));
        assertThat(scanDB().count(), is(equalTo(2)));
    }

    @Test
    public void shouldOverwriteExistingCandidateWhenUpdating() {
        var originalCandidate = TestUtils.randomCandidate();
        var created = candidateRepository.create(originalCandidate, List.of());
        var newCandidate = originalCandidate.copy().publicationBucketUri(randomUri()).build();
        candidateRepository.update(created.identifier(), newCandidate, List.of());
        var fetched = candidateRepository.findCandidateById(created.identifier()).get().candidate();

        assertThat(scanDB().count(), is(equalTo(2)));
        assertThat(fetched, is(not(equalTo(originalCandidate))));
        assertThat(fetched, is(equalTo(newCandidate)));
    }

    @Test
    void shouldFetchCandidatesByGivenYearAndPageSize() {
        int year = Integer.parseInt(randomYear());
        var candidates = createNumberOfCandidatesForYear(year, 10);
        int pageSize = 5;
        var expectedCandidates = sortByIdentifier(candidates, pageSize);
        var results = candidateRepository.fetchCandidatesByYear(year, pageSize, null);
        assertThat(results.size(), is(equalTo(pageSize)));
        assertThat(expectedCandidates, containsInAnyOrder(results.toArray()));
    }

    @Test
    void shouldFetchCandidatesByGivenYearAndStartMarker() {
        int year = Integer.parseInt(randomYear());
        var candidates = createNumberOfCandidatesForYear(year, 2);
        var expectedCandidates = sortByIdentifier(candidates, DEFAULT_PAGE_SIZE);
        var startMarker = getStartMarker(expectedCandidates.get(0));
        var results = candidateRepository.fetchCandidatesByYear(year, null, startMarker);
        assertThat(results.size(), is(equalTo(1)));
        assertThat(expectedCandidates.subList(1, 2), containsInAnyOrder(results.toArray()));
    }

    @Test
    void shouldFetchCandidatesByGivenYearWithDefaultPageSizeAndStartMarkerIfNotSet() {
        int year = Integer.parseInt(randomYear());
        int numberOfCandidates = DEFAULT_PAGE_SIZE + randomIntBetween(1, 10);
        var candidates = createNumberOfCandidatesForYear(year, numberOfCandidates);
        var expectedCandidates = sortByIdentifier(candidates, DEFAULT_PAGE_SIZE);
        var results = candidateRepository.fetchCandidatesByYear(year, null, null);
        assertThat(results.size(), is(equalTo(DEFAULT_PAGE_SIZE)));
        assertThat(expectedCandidates, containsInAnyOrder(results.toArray()));
    }

    private static Map<String, String> getStartMarker(CandidateDao dao) {
        return Map.of("PrimaryKeyRangeKey", dao.primaryKeyRangeKey(),
                      "PrimaryKeyHashKey", dao.primaryKeyHashKey(),
                      "SearchByYearHashKey", String.valueOf(dao.searchByYearHashKey()),
                      "SearchByYearRangeKey", dao.searchByYearSortKey());
    }

    private static DbCandidate randomCandidate(int year) {
        return randomCandidateBuilder(true).publicationDate(publicationDate(year)).build();
    }

    private static DbPublicationDate publicationDate(int year) {
        return new DbPublicationDate(String.valueOf(year), null, null);
    }

    private static List<CandidateDao> sortByIdentifier(List<CandidateDao> candidates, int limit) {
        var comparator = Comparator.comparing(CandidateRepositoryTest::getCharacterValues);
        return candidates.stream()
                   .sorted(Comparator.comparing(CandidateDao::identifier, comparator))
                   .limit(limit)
                   .toList();
    }

    private static String getCharacterValues(UUID uuid) {
        return uuid.toString().replaceAll(UUID_SEPERATOR, "");
    }

    private List<CandidateDao> createNumberOfCandidatesForYear(int year, int number) {
        return IntStream.range(0, number)
                   .mapToObj(i -> randomCandidate(year))
                   .map(candidate -> candidateRepository.createDao(candidate, List.of()))
                   .toList();
    }
}