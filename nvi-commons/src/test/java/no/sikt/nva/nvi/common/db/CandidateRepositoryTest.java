package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.test.TestUtils.createNumberOfCandidatesForYear;
import static no.sikt.nva.nvi.test.TestUtils.getYearIndexStartMarker;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.sikt.nva.nvi.test.TestUtils.randomIntBetween;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.sikt.nva.nvi.test.TestUtils.sortByIdentifier;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateRepositoryTest extends LocalDynamoTest {

    public static final int DEFAULT_PAGE_SIZE = 700;
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
        var searchYear = randomYear();
        var candidates = createNumberOfCandidatesForYear(searchYear, 10, candidateRepository);
        createNumberOfCandidatesForYear(randomYear(), 10, candidateRepository);
        int pageSize = 5;
        var expectedCandidates = sortByIdentifier(candidates, pageSize);
        var results = candidateRepository.fetchCandidatesByYear(searchYear, pageSize, null).getCandidates();
        assertThat(results.size(), is(equalTo(pageSize)));
        assertThat(expectedCandidates, containsInAnyOrder(results.toArray()));
    }

    @Test
    void shouldFetchCandidatesByGivenYearAndStartMarker() {
        var year = randomYear();
        var candidates = createNumberOfCandidatesForYear(year, 2, candidateRepository);
        var expectedCandidates = sortByIdentifier(candidates, DEFAULT_PAGE_SIZE);
        var firstCandidateInIndex = expectedCandidates.get(0);
        var secondCandidateInIndex = expectedCandidates.get(1);
        var startMarker = getYearIndexStartMarker(firstCandidateInIndex);
        var results = candidateRepository.fetchCandidatesByYear(year, null, startMarker).getCandidates();
        assertThat(results.size(), is(equalTo(1)));
        assertEquals(secondCandidateInIndex, results.get(0));
    }

    @Test
    void shouldFetchCandidatesByGivenYearWithDefaultPageSizeAndStartMarkerIfNotSet() {
        var year = randomYear();
        int numberOfCandidates = DEFAULT_PAGE_SIZE + randomIntBetween(1, 10);
        var candidates = createNumberOfCandidatesForYear(year, numberOfCandidates, candidateRepository);
        var expectedCandidates = sortByIdentifier(candidates, DEFAULT_PAGE_SIZE);
        var results = candidateRepository.fetchCandidatesByYear(year, null, null).getCandidates();
        assertThat(results.size(), is(equalTo(DEFAULT_PAGE_SIZE)));
        assertThat(expectedCandidates, containsInAnyOrder(results.toArray()));
    }
}