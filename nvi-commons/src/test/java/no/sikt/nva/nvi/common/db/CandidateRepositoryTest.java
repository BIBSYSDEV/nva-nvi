package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateRepositoryTest extends LocalDynamoTest {

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
        var originalCandidate = randomCandidate();
        var created = candidateRepository.create(originalCandidate, List.of());
        var newCandidate = originalCandidate.copy().publicationBucketUri(randomUri()).build();
        candidateRepository.update(created.identifier(), newCandidate, List.of());
        var fetched = candidateRepository.findCandidateById(created.identifier()).get().candidate();

        assertThat(scanDB().count(), is(equalTo(2)));
        assertThat(fetched, is(not(equalTo(originalCandidate))));
        assertThat(fetched, is(equalTo(newCandidate)));
    }


}