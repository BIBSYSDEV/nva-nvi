package no.sikt.nva.nvi.common;

import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import no.sikt.nva.nvi.common.model.business.CompletionStatus;
import no.sikt.nva.nvi.common.model.business.Period;
import no.sikt.nva.nvi.common.model.business.ReportingStatus;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Test;

public record ReportingStatusTest() {

    @Test
    void shouldMakeRoundTripWithoutLossOfInformation() throws JsonProcessingException {
        var status = randomReportingStatus();
        var json = JsonUtils.dtoObjectMapper.writeValueAsString(status);
        var reconstructedStatus = JsonUtils.dtoObjectMapper.readValue(json, ReportingStatus.class);
        assertThat(reconstructedStatus, is(equalTo(status)));
    }

    private static Period randomPeriod() {
        return new Period.Builder().withYear(randomInteger())
                   .withStart(randomInstant())
                   .withEnd(randomInstant())
                   .build();
    }

    private ReportingStatus randomReportingStatus() {
        return new ReportingStatus.Builder()
                   .withInstitutionId(randomUri())
                   .withPeriod(randomPeriod())
                   .withStatus(CompletionStatus.COMPLETED)
                   .withUpdatedDate(Instant.now())
                   .build();
    }
}
