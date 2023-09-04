package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.test.TestUtils.randomNviPeriodBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NviPeriodRepositoryTest extends LocalDynamoTest {

    private NviPeriodRepository nviPeriodRepository;

    @BeforeEach
    public void setUp() {
        localDynamo = initializeTestDatabase();
        nviPeriodRepository = new NviPeriodRepository(localDynamo);
    }

    @Test
    public void shouldUpdateExistingRecordWhenSavedSecondTime() {

        var year = TestUtils.randomYear();
        var user1 = TestUtils.randomUsername();
        var user2 = TestUtils.randomUsername();

        var nviPeriod1 = randomNviPeriodBuilder().withPublishingYear(year).withModifiedBy(user1).build();
        var nviPeriod2 = randomNviPeriodBuilder().withPublishingYear(year).withModifiedBy(user2).build();
        nviPeriodRepository.save(nviPeriod1);
        nviPeriodRepository.save(nviPeriod2);
        var tableItemCount = scanDB().count();
        assertThat(tableItemCount, is(equalTo(1)));

        var fetched = nviPeriodRepository.findByPublishingYear(year);
        assertThat(fetched.get().modifiedBy(), is(equalTo(user2)));
    }
}