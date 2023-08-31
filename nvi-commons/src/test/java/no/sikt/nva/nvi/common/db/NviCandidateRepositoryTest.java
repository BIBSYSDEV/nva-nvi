package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

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
        nviCandidateRepository.save(candidate1);
        assertThrows(RuntimeException.class, () -> nviCandidateRepository.save(candidate2));
        var tableItemCount = localDynamo.scan(ScanRequest.builder().tableName(NVI_TABLE_NAME).build()).count();
        assertThat(tableItemCount, is(equalTo(2)));
    }
}