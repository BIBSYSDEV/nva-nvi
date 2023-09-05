package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NviCandidateRepositoryTest extends LocalDynamoTest {

    private NviCandidateRepository nviCandidateRepository;

    @BeforeEach
    public void setUp() {
        localDynamo = initializeTestDatabase();
        nviCandidateRepository = new NviCandidateRepository(localDynamo);
    }

    @Test
    public void shouldThrowExceptionWhenAttemptingToSaveCandidateWithExistingPublicationId() {
        var publicationId = randomUri();
        var candidate1 = randomCandidateBuilder().withPublicationId(publicationId).build();
        var candidate2 = randomCandidateBuilder().withPublicationId(publicationId).build();
        nviCandidateRepository.create(candidate1);
        assertThrows(RuntimeException.class, () -> nviCandidateRepository.create(candidate2));
        assertThat(scanDB().count(), is(equalTo(2)));
    }

    @Test
    public void shouldOverwriteExistingCandidateWhenUpdating() {
        var originalCandidate = randomCandidate();
        var created = nviCandidateRepository.create(originalCandidate);
        var newCandidate = originalCandidate.copy().withPublicationBucketUri(randomUri()).build();
        nviCandidateRepository.update(created.identifier(),newCandidate);
        var fetched = nviCandidateRepository.findById(created.identifier()).get().candidate();

        assertThat(scanDB().count(), is(equalTo(2)));
        assertThat(fetched, is(not(equalTo(originalCandidate))));
        assertThat(fetched, is(equalTo(newCandidate)));
    }


}