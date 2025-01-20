package no.sikt.nva.nvi.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NviPeriodServiceTest extends LocalDynamoTest {

  private PeriodRepository periodRepository;
  private NviPeriodService nviPeriodService;

  @BeforeEach
  void setup() {
    periodRepository = new PeriodRepository(initializeTestDatabase());
    nviPeriodService = new NviPeriodService(periodRepository);
  }

  @Test
  void shouldReturnAllPeriods() {
    persistPeriod(2022);
    persistPeriod(2023);
    assertEquals(2, nviPeriodService.fetchAll().size());
  }

  @Test
  void shouldReturnLatestClosedPeriodYear() {
    persistPeriod(2022);
    persistPeriod(2023);
    assertTrue(nviPeriodService.fetchLatestClosedPeriodYear().isPresent());
    assertEquals(2023, nviPeriodService.fetchLatestClosedPeriodYear().get());
  }

  private void persistPeriod(int publishingYear) {
    periodRepository.save(
        DbNviPeriod.builder()
            .publishingYear(String.valueOf(publishingYear))
            .startDate(LocalDateTime.of(publishingYear, 4, 1, 0, 0, 0).toInstant(ZoneOffset.UTC))
            .reportingDate(
                LocalDateTime.of(publishingYear + 1, 3, 1, 0, 0, 0).toInstant(ZoneOffset.UTC))
            .build());
  }
}
