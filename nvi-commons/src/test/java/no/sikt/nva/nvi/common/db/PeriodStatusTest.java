package no.sikt.nva.nvi.common.db;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PeriodStatusTest {

    private static final ZonedDateTime now = ZonedDateTime.now();

    public static Stream<Arguments> periodToPeriodStatusProvider() {
        return Stream.of(Arguments.of(DbNviPeriod.builder()
                                          .startDate(minusMonthFromNow(1))
                                          .reportingDate(plusMonthsFromNow(1))
                                          .build(), Status.OPEN_PERIOD),
                         Arguments.of(DbNviPeriod.builder()
                                          .startDate(minusMonthFromNow(2))
                                          .reportingDate(minusMonthFromNow(1))
                                          .build(), Status.CLOSED_PERIOD),
                         Arguments.of(DbNviPeriod.builder()
                                          .startDate(plusMonthsFromNow(1))
                                          .reportingDate(plusMonthsFromNow(2))
                                          .build(), Status.UNOPENED_PERIOD));
    }

    @Test
    void shouldConvertPeriodStatusToDto() {
        var status = Status.NO_PERIOD;
        var reportingDate = Instant.now();
        var startDate = Instant.now();
        var periodStatus = PeriodStatus.builder()
                               .withStartDate(startDate)
                               .withReportingDate(reportingDate)
                               .withStatus(status)
                               .withYear(randomString())
                               .build();
        var expectedDto = PeriodStatusDto.builder()
                              .withStatus(PeriodStatusDto.Status.parse(status.getValue()))
                              .withStartDate(startDate.toString())
                              .withReportingDate(reportingDate.toString())
                              .withYear(periodStatus.year())
                              .build();
        var actualDto = PeriodStatusDto.fromPeriodStatus(periodStatus);
        assertThat(actualDto, is(equalTo(expectedDto)));
        assertThat(actualDto.startDate(), is(equalTo(expectedDto.startDate())));
        assertThat(actualDto.year(), is(equalTo(expectedDto.year())));
    }

    @ParameterizedTest()
    @MethodSource("periodToPeriodStatusProvider")
    void shouldConvertPeriodToPeriodStatusCorrectly(DbNviPeriod period, Status expectedStatus) {
        var actualStatus = PeriodStatus.fromPeriod(period);
        var actualPeriodDtoStatus = PeriodStatusDto.fromPeriodStatus(actualStatus);

        assertThat(actualStatus.status(), is(equalTo(expectedStatus)));
        assertThat(actualPeriodDtoStatus.reportingDate(), is(equalTo(actualStatus.reportingDate().toString())));
    }

    private static Instant minusMonthFromNow(int numberOfMonths) {
        return now.minusMonths(numberOfMonths).toInstant();
    }

    private static Instant plusMonthsFromNow(int numberOfMonths) {
        return now.plusMonths(numberOfMonths).toInstant();
    }
}
