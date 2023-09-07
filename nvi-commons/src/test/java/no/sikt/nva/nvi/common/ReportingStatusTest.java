package no.sikt.nva.nvi.common;

import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.model.DbCompletionStatus;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbReportingStatus;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Test;

public record ReportingStatusTest() {

    @Test
    void shouldMakeRoundTripWithoutLossOfInformation() throws JsonProcessingException {
        var status = randomReportingStatus();
        var json = JsonUtils.dtoObjectMapper.writeValueAsString(status);
        var reconstructedStatus = JsonUtils.dtoObjectMapper.readValue(json, DbReportingStatus.class);
        assertThat(reconstructedStatus, is(equalTo(status)));
    }

    @Test
    void shouldCreateCopyOfApprovalPeriod() {
        var period = randomPeriod();
        var copy = period.copy().reportingDate(null).build();
        assertThat(period, is(not(equalTo(copy))));
    }

    private static DbNviPeriod randomPeriod() {
        return DbNviPeriod.builder().publishingYear(randomString())
                                      .reportingDate(randomInstant())
                                      .createdBy(new DbUsername(randomString()))
                                      .modifiedBy(new DbUsername(randomString()))
                                      .build();
    }

    private DbReportingStatus randomReportingStatus() {
        return  DbReportingStatus.builder()
                   .institutionId(randomUri())
                   .nviPeriod(randomPeriod())
                   .status(DbCompletionStatus.COMPLETED)
                   .updatedDate(Instant.now())
                   .build();
    }
}
