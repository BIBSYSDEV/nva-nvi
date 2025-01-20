package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.test.TestUtils.randomNviPeriodBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PeriodRepositoryTest extends LocalDynamoTest {

  private PeriodRepository periodRepository;

  @BeforeEach
  void setUp() {
    localDynamo = initializeTestDatabase();
    periodRepository = new PeriodRepository(localDynamo);
  }

  @Test
  void shouldUpdateExistingRecordWhenSavedSecondTime() {

    var year = TestUtils.randomYear();
    var user1 = TestUtils.randomUsername();
    var user2 = TestUtils.randomUsername();

    var nviPeriod1 = randomNviPeriodBuilder().publishingYear(year).modifiedBy(user1).build();
    var nviPeriod2 = randomNviPeriodBuilder().publishingYear(year).modifiedBy(user2).build();
    periodRepository.save(nviPeriod1);
    periodRepository.save(nviPeriod2);
    var tableItemCount = scanDB().count();
    assertThat(tableItemCount, is(equalTo(1)));

    var fetched = periodRepository.findByPublishingYear(year);
    assertThat(fetched.get().modifiedBy(), is(equalTo(user2)));
  }
}
