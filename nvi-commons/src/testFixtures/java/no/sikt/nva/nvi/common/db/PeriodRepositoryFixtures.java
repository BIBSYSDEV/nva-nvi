package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getGlobalEnvironment;
import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomUsername;

import java.time.Instant;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.CreatePeriodRequest;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.common.service.model.UpdatePeriodRequest;
import no.sikt.nva.nvi.common.service.model.Username;

public class PeriodRepositoryFixtures {
  private static final Instant previousMonth = ZonedDateTime.now().minusMonths(1).toInstant();
  private static final Instant previousYear = ZonedDateTime.now().minusMonths(12).toInstant();
  private static final Instant nextMonth = ZonedDateTime.now().plusMonths(1).toInstant();
  private static final Instant nextYear = ZonedDateTime.now().plusMonths(12).toInstant();

  public static NviPeriod setupFuturePeriod(TestScenario scenario, String year) {
    return upsertPeriod(year, nextMonth, nextYear, scenario.getPeriodRepository());
  }

  public static NviPeriod setupFuturePeriod(TestScenario scenario, int year) {
    return setupFuturePeriod(scenario, String.valueOf(year));
  }

  public static NviPeriod setupOpenPeriod(TestScenario scenario, String year) {
    return upsertPeriod(year, previousMonth, nextYear, scenario.getPeriodRepository());
  }

  public static NviPeriod setupOpenPeriod(TestScenario scenario, int year) {
    return setupOpenPeriod(scenario, String.valueOf(year));
  }

  public static NviPeriod setupClosedPeriod(TestScenario scenario, String year) {
    return upsertPeriod(year, previousYear, previousMonth, scenario.getPeriodRepository());
  }

  public static NviPeriod setupClosedPeriod(TestScenario scenario, int year) {
    return setupClosedPeriod(scenario, String.valueOf(year));
  }

  public static NviPeriod upsertPeriod(
      String year, Instant startDate, Instant reportingDate, PeriodRepository periodRepository) {
    var periodService = new NviPeriodService(getGlobalEnvironment(), periodRepository);
    var existingPeriod = periodService.findByPublishingYear(year);
    if (existingPeriod.isPresent()) {
      return updatePeriod(year, startDate, reportingDate, periodRepository);
    }
    return createPeriod(year, startDate, reportingDate, periodRepository);
  }

  public static NviPeriod createPeriod(
      String year, Instant startDate, Instant reportingDate, PeriodRepository periodRepository) {
    var user = Username.fromUserName(randomUsername());
    var request =
        CreatePeriodRequest.builder()
            .withPublishingYear(Integer.parseInt(year))
            .withStartDate(startDate)
            .withReportingDate(reportingDate)
            .withCreatedBy(user)
            .build();

    var periodService = new NviPeriodService(getGlobalEnvironment(), periodRepository);
    periodService.create(request);
    return periodService.getByPublishingYear(year);
  }

  public static NviPeriod updatePeriod(
      String year, Instant startDate, Instant reportingDate, PeriodRepository periodRepository) {
    var user = Username.fromUserName(randomUsername());
    var request =
        UpdatePeriodRequest.builder()
            .withPublishingYear(Integer.parseInt(year))
            .withStartDate(startDate)
            .withReportingDate(reportingDate)
            .withModifiedBy(user)
            .build();

    var periodService = new NviPeriodService(getGlobalEnvironment(), periodRepository);
    periodService.update(request);
    return periodService.getByPublishingYear(year);
  }
}
