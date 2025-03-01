package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.initializeTestDatabase;
import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.scanDB;
import static no.sikt.nva.nvi.common.db.DbNviPeriodFixtures.randomNviPeriodBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class PeriodRepositoryTest {

  private PeriodRepository periodRepository;
  private DynamoDbClient localDynamo;

  @BeforeEach
  void setUp() {
    localDynamo = initializeTestDatabase();
    periodRepository = new PeriodRepository(localDynamo);
  }

  @Test
  void shouldUpdateExistingRecordWhenSavedSecondTime() {

    var year = TestUtils.randomYear();
    var user1 = UsernameFixtures.randomUsername();
    var user2 = UsernameFixtures.randomUsername();

    var nviPeriod1 = randomNviPeriodBuilder().publishingYear(year).modifiedBy(user1).build();
    var nviPeriod2 = randomNviPeriodBuilder().publishingYear(year).modifiedBy(user2).build();
    periodRepository.save(nviPeriod1);
    periodRepository.save(nviPeriod2);
    var tableItemCount = scanDB(localDynamo).count();
    assertThat(tableItemCount, is(equalTo(1)));

    var fetched = periodRepository.findByPublishingYear(year);
    assertThat(fetched.get().modifiedBy(), is(equalTo(user2)));
  }
}
