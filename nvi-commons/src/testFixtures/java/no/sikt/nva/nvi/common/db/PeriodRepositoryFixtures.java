package no.sikt.nva.nvi.common.db;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.service.model.CreatePeriodRequest;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.common.service.model.UpdatePeriodRequest;
import no.sikt.nva.nvi.common.service.model.Username;

public class PeriodRepositoryFixtures {
  private static final Instant previousMonth = ZonedDateTime.now().minusMonths(1).toInstant();
  private static final Instant previousYear = ZonedDateTime.now().minusMonths(12).toInstant();
  private static final Instant nextMonth = ZonedDateTime.now().plusMonths(1).toInstant();
  private static final Instant nextYear = ZonedDateTime.now().plusMonths(12).toInstant();

  public static PeriodRepository periodRepositoryReturningClosedPeriod(int year) {
    var period =
        DbNviPeriod.builder()
            .publishingYear(String.valueOf(year))
            .startDate(previousYear)
            .reportingDate(previousMonth)
            .build();
    return mockPeriodRepositoryReturn(period);
  }

  private static PeriodRepository mockPeriodRepositoryReturn(DbNviPeriod period) {
    var nviPeriodRepository = mock(PeriodRepository.class);
    when(nviPeriodRepository.findByPublishingYear(anyString())).thenReturn(Optional.of(period));
    return nviPeriodRepository;
  }

  public static PeriodRepository periodRepositoryReturningNotOpenedPeriod(int year) {
    var period =
        DbNviPeriod.builder()
            .publishingYear(String.valueOf(year))
            .startDate(nextMonth)
            .reportingDate(nextYear)
            .build();
    return mockPeriodRepositoryReturn(period);
  }

  public static PeriodRepository periodRepositoryReturningOpenedPeriod(int year) {
    var period =
        DbNviPeriod.builder()
            .publishingYear(String.valueOf(year))
            .id(randomUri())
            .startDate(previousMonth)
            .reportingDate(nextYear)
            .build();
    return mockPeriodRepositoryReturn(period);
  }

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

  private static NviPeriod upsertPeriod(
      String year, Instant startDate, Instant reportingDate, PeriodRepository periodRepository) {
    var user = Username.fromString(randomString());
    var existingPeriod = periodRepository.findByPublishingYear(year);
    if (existingPeriod.isPresent()) {
      var request =
          UpdatePeriodRequest.builder()
              .withPublishingYear(Integer.parseInt(year))
              .withStartDate(startDate)
              .withReportingDate(reportingDate)
              .withModifiedBy(user)
              .build();
      return NviPeriod.update(request, periodRepository);
    }
    var request =
        CreatePeriodRequest.builder()
            .withPublishingYear(Integer.parseInt(year))
            .withStartDate(startDate)
            .withReportingDate(reportingDate)
            .withCreatedBy(user)
            .build();
    return NviPeriod.create(request, periodRepository);
  }
}
