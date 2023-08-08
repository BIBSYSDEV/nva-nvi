package no.sikt.nva.nvi.common;

import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import no.sikt.nva.nvi.common.model.Candidate;
import no.sikt.nva.nvi.common.model.CompletionStatus;
import no.sikt.nva.nvi.common.model.InstitutionReportingStatus;
import no.sikt.nva.nvi.common.model.NviPeriod;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Test;

public record InstitutionReportingStatusTest() {

    @Test
    void shouldMakeRoundTripWithoutLossOfInformation() throws JsonProcessingException {
        var status = randomReportingStatus();
        var json = JsonUtils.dtoObjectMapper.writeValueAsString(status);
        var reconstructedStatus = JsonUtils.dtoObjectMapper.readValue(json, InstitutionReportingStatus.class);
        assertThat(reconstructedStatus, is(equalTo(status)));
    }

    private InstitutionReportingStatus randomReportingStatus() {
        return new InstitutionReportingStatus.Builder()
                   .withInstitutionId(randomUri())
                   .withPeriod(randomPeriod())
                   .withStatus(CompletionStatus.COMPLETED)
                   .withUpdatedDate(Instant.now())
                   .build();
    }

    private static NviPeriod randomPeriod() {
        return new NviPeriod.Builder().withYear(randomInteger()).withEnd(Instant.now()).build();
    }
}
