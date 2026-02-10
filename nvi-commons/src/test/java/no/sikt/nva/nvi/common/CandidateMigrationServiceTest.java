package no.sikt.nva.nvi.common;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateMigrationServiceTest {

  private CandidateMigrationService migrationService;

  @BeforeEach
  void setUp() {
    var scenario = new TestScenario();
    migrationService = new CandidateMigrationService();
    setupOpenPeriod(scenario, CURRENT_YEAR);
  }

  @Test
  void shouldLogWhenMigrationNviCandidate() {
    var appender = LogUtils.getTestingAppenderForRootLogger();
    UUID identifier = randomUUID();
    migrationService.migrateCandidate(identifier);

    assertThat(appender.getMessages()).contains("Migrating candidate: %s".formatted(identifier));
  }
}
