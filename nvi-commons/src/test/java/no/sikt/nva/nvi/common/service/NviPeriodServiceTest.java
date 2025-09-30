package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getGlobalEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomUsername;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.datafaker.Faker;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.common.service.model.UpdatePeriodRequest;
import no.sikt.nva.nvi.common.service.model.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NviPeriodServiceTest {
  private static final Faker FAKER = new Faker();
  private TestScenario scenario;
  private NviPeriodService periodService;

  @BeforeEach
  void setup() {
    scenario = new TestScenario();
    periodService = new NviPeriodService(getGlobalEnvironment(), scenario.getPeriodRepository());
  }

  @Test
  void shouldReturnAllPeriods() {
    setupClosedPeriod(scenario, CURRENT_YEAR - 1);
    setupOpenPeriod(scenario, CURRENT_YEAR);
    assertEquals(2, periodService.getAll().size());
  }

  @Test
  void shouldUpdateExistingRecordOnUpdate() {
    var year = String.valueOf(CURRENT_YEAR);
    var originalPeriod = setupOpenPeriod(scenario, year);

    var expectedUser = Username.fromUserName(randomUsername());
    var updateRequest = toUpdateRequestBuilder(originalPeriod).withModifiedBy(expectedUser);
    periodService.update(updateRequest.build());

    var actualPeriod = scenario.getPeriodRepository().findByPublishingYear(year).orElseThrow();
    assertThat(actualPeriod.nviPeriod().modifiedBy().value()).isEqualTo(expectedUser.value());
  }

  private static UpdatePeriodRequest.Builder toUpdateRequestBuilder(NviPeriod period) {
    return UpdatePeriodRequest.builder()
        .withPublishingYear(period.publishingYear())
        .withStartDate(period.startDate())
        .withReportingDate(period.reportingDate())
        .withModifiedBy(period.modifiedBy());
  }
}
