package no.sikt.nva.nvi.common.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.PeriodStatus;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import org.junit.jupiter.api.Test;

public class PeriodStatusTest {

    @Test
    void shouldConvertPeriodStatusToDto() {
        var status = Status.NO_PERIOD;
        var reportingDate = Instant.now();
        var startDate = Instant.now();
        var periodStatus = PeriodStatus.builder()
                               .withStartDate(startDate)
                               .withReportingDate(reportingDate)
                               .withStatus(status)
                               .build();
        var expectedDto = PeriodStatusDto.builder()
                              .withStatus(PeriodStatusDto.Status.parse(status.getValue()))
                              .withStartDate(startDate.toString())
                              .withReportingDate(reportingDate.toString())
                              .build();
        var actualDto = PeriodStatusDto.fromPeriodStatus(periodStatus);
        assertThat(actualDto, is(equalTo(expectedDto)));
        assertThat(actualDto.startDate(), is(equalTo(expectedDto.startDate())));
    }
}
