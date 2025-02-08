package no.sikt.nva.nvi.common.db;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.service.model.CreatePeriodRequest;
import no.sikt.nva.nvi.common.service.model.NviPeriod;

public class PeriodRepositoryFixtures {

  public static PeriodRepository periodRepositoryReturningClosedPeriod(int year) {
    var period =
        DbNviPeriod.builder()
            .publishingYear(String.valueOf(year))
            .startDate(ZonedDateTime.now().minusMonths(10).toInstant())
            .reportingDate(ZonedDateTime.now().minusMonths(1).toInstant())
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
            .startDate(ZonedDateTime.now().plusMonths(1).toInstant())
            .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant())
            .build();
    return mockPeriodRepositoryReturn(period);
  }

  public static PeriodRepository periodRepositoryReturningOpenedPeriod(int year) {
    var period =
        DbNviPeriod.builder()
            .publishingYear(String.valueOf(year))
            .id(randomUri())
            .startDate(Instant.now())
            .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant())
            .build();
    return mockPeriodRepositoryReturn(period);
  }

  public static NviPeriod setupPersistedPeriod(String year, PeriodRepository periodRepository) {
    return NviPeriod.create(
        CreatePeriodRequest.builder()
            .withPublishingYear(Integer.parseInt(year))
            .withStartDate(ZonedDateTime.now().plusMonths(1).toInstant())
            .withReportingDate(ZonedDateTime.now().plusMonths(10).toInstant())
            .withCreatedBy(no.sikt.nva.nvi.common.service.model.Username.fromString(randomString()))
            .build(),
        periodRepository);
  }
}
