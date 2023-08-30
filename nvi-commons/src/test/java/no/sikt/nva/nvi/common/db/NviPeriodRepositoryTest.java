package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.ApplicationConstants.NVI_TABLE_NAME;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.sikt.nva.nvi.test.TestUtils.randomNviPeriodBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

class NviPeriodRepositoryTest extends LocalDynamoTest {

    private NviPeriodRepository nviPeriodRepository;

    @BeforeEach
    public void setUp() {
        localDynamo = initializeTestDatabase();
        nviPeriodRepository = new NviPeriodRepository(localDynamo);
    }

    @Test
    public void shouldUpdateExistingRecordWhenSavedSecondTime() {

        String year = "1989";
        var user1 = TestUtils.randomUsername();
        var user2 = TestUtils.randomUsername();

        var nviPeriod1 = randomNviPeriodBuilder().withPublishingYear(year).withModifiedBy(user1).build();
        var nviPeriod2 = randomNviPeriodBuilder().withPublishingYear(year).withModifiedBy(user2).build();
        nviPeriodRepository.save(nviPeriod1);
        nviPeriodRepository.save(nviPeriod2);
        var tableItemCount = localDynamo.scan(ScanRequest.builder().tableName(NVI_TABLE_NAME).build()).count();
        assertThat(tableItemCount, is(equalTo(1)));

        var fetched = nviPeriodRepository.findByYear(year);
        assertThat(fetched.get().modifiedBy(), is(equalTo(user2)));
    }
}