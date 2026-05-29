package no.sikt.nva.nvi.common.db;

import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbCompletionStatus;
import no.sikt.nva.nvi.common.db.model.DbReportingStatus;
import no.sikt.nva.nvi.common.db.model.Username;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Test;

record ReportingStatusTest() {

  @Test
  void shouldMakeRoundTripWithoutLossOfInformation() throws JsonProcessingException {
    var status = randomReportingStatus();
    var json = JsonUtils.dtoObjectMapper.writeValueAsString(status);
    var reconstructedStatus = JsonUtils.dtoObjectMapper.readValue(json, DbReportingStatus.class);
    assertEquals(status, reconstructedStatus);
  }

  @Test
  void shouldCreateCopyOfApprovalPeriod() {
    var period = randomPeriod();
    var copy = period.copy().reportingDate(null).build();
    assertNotEquals(copy, period);
  }

  private static DbNviPeriod randomPeriod() {
    return DbNviPeriod.builder()
        .publishingYear(randomString())
        .reportingDate(randomInstant())
        .createdBy(Username.fromString(randomString()))
        .modifiedBy(Username.fromString(randomString()))
        .build();
  }

  private DbReportingStatus randomReportingStatus() {
    return DbReportingStatus.builder()
        .institutionId(randomUri())
        .nviPeriod(randomPeriod())
        .status(DbCompletionStatus.COMPLETED)
        .updatedDate(Instant.now())
        .build();
  }
}
