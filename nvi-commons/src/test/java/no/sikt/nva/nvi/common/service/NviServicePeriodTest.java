package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.createPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NviServicePeriodTest extends LocalDynamoTest {

    public static final int YEAR = ZonedDateTime.now().getYear();

    private NviService nviService;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        nviService = TestUtils.nviServiceReturningOpenPeriod(localDynamo, YEAR);
    }

    @Test
    void shouldCreateNviPeriod() {
        var period = createPeriod("2050");
        var nviService = new NviService(localDynamo);
        nviService.createPeriod(period);

        var expectedId = UriWrapper.fromHost(new Environment().readEnv("API_HOST"))
                             .addChild("scientific-index")
                             .addChild("period")
                             .addChild(period.publishingYear())
                             .getUri();

        var expectedPeriod = period.copy().id(expectedId).build();

        assertThat(nviService.getPeriod(period.publishingYear()), is(equalTo(expectedPeriod)));
    }

    @Test
    void shouldThrowIllegalArgumentWhenPeriodMissedMandatoryValues() {
        var period = new DbNviPeriod(randomUri(), String.valueOf(ZonedDateTime.now().plusYears(1).getYear()), null,
                                     ZonedDateTime.now().plusMonths(10).toInstant(), new Username(randomString()),
                                     null);
        var nviService = new NviService(localDynamo);

        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldUpdateNviPeriod() {
        var originalPeriod = createPeriod(String.valueOf(ZonedDateTime.now().getYear()));
        nviService.createPeriod(originalPeriod);
        nviService.updatePeriod(
            originalPeriod.copy().reportingDate(originalPeriod.reportingDate().plusSeconds(500)).build());
        var fetchedPeriod = nviService.getPeriod(originalPeriod.publishingYear());
        assertThat(fetchedPeriod, is(not(equalTo(originalPeriod))));
    }

    @Test
    void shouldNotAllowNviPeriodReportingDateInInPast() {
        var originalPeriod = createPeriod(String.valueOf(ZonedDateTime.now().getYear()));
        nviService.createPeriod(originalPeriod);
        var updatedPeriod = originalPeriod.copy().reportingDate(ZonedDateTime.now().minusWeeks(10).toInstant()).build();

        assertThrows(IllegalArgumentException.class, () -> nviService.updatePeriod(updatedPeriod));
    }

    @Test
    void shouldNotAllowNviPeriodStartAfterReportingDate() {
        var originalPeriod = createPeriod(String.valueOf(ZonedDateTime.now().getYear()));
        nviService.createPeriod(originalPeriod);
        var updatedPeriod = originalPeriod.copy().startDate(ZonedDateTime.now().plusYears(1).toInstant()).build();
        assertThrows(IllegalArgumentException.class, () -> nviService.updatePeriod(updatedPeriod));
    }

    @Test
    void shouldReturnIllegalArgumentExceptionWhenPublishingYearIsNotAYear() {
        var period = createPeriod("20AA");
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentExceptionWhenPublishingYearHasInvalidLength() {
        var period = createPeriod("22");
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentExceptionWhenWhenStartDateHasAlreadyBeenReached() {
        var period = DbNviPeriod.builder()
                         .startDate(ZonedDateTime.now().minusDays(1).toInstant())
                         .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant())
                         .publishingYear(String.valueOf(ZonedDateTime.now().getYear()))
                         .createdBy(randomUsername())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentWhenReportingDateIsBeforeNow() {
        var period = DbNviPeriod.builder()
                         .reportingDate(Instant.MIN)
                         .startDate(ZonedDateTime.now().plusMonths(1).toInstant())
                         .publishingYear("2023")
                         .createdBy(Username.builder().value("me").build())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentWhenStartDateIsAfterReportingDate() {
        var period = DbNviPeriod.builder()
                         .startDate(ZonedDateTime.now().plusMonths(10).toInstant())
                         .reportingDate(ZonedDateTime.now().plusMonths(1).toInstant())
                         .publishingYear(String.valueOf(ZonedDateTime.now().getYear()))
                         .createdBy(Username.builder().value("me").build())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentWhenPublishingYearIsNotAValidYear() {
        var period = DbNviPeriod.builder()
                         .reportingDate(Instant.MIN)
                         .publishingYear("now!")
                         .createdBy(Username.builder().value("me").build())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnPeriodsOnlyWhenFetchingPeriods() {
        var nviService = new NviService(localDynamo);
        nviService.createPeriod(createPeriod(String.valueOf(ZonedDateTime.now().getYear())));
        nviService.createPeriod(createPeriod(String.valueOf(ZonedDateTime.now().plusYears(1).getYear())));
        var periods = nviService.getPeriods();
        assertThat(periods, hasSize(2));
    }

    @Test
    void shouldThrowPeriodNotFoundExceptionWhenPeriodDoesNotExist() {
        assertThrows(PeriodNotFoundException.class, () -> new NviService(localDynamo).getPeriod(randomYear()));
    }

    private static Username randomUsername() {
        return Username.fromString(randomString());
    }
}
