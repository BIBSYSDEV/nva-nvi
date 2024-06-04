package no.sikt.nva.nvi.common.service.model;

import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NviPeriodTest extends LocalDynamoTest {

    private static final int YEAR = LocalDateTime.now().getYear();
    private PeriodRepository periodRepository;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        periodRepository = new PeriodRepository(localDynamo);
        setUpExtingPeriod();
    }

    @Test
    void shouldCreateNviPeriod() {
        var request = createRequest(YEAR + 1);
        var expectedId = constructExpectedId(request);
        var expected = new NviPeriod(expectedId, request.publishingYear(), request.startDate(),
                                     request.reportingDate());
        var actual = NviPeriod.upsert(request, periodRepository);
        assertEquals(expected, actual);
    }

    @Test
    void shouldThrowIllegalArgumentWhenPeriodMissedMandatoryValues() {
        var createPeriodRequest = UpsertPeriodRequest.builder()
                                      .withPublishingYear(YEAR + 1)
                                      .withCreatedBy(randomUserName())
                                      .buildCreateRequest();
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.upsert(createPeriodRequest, periodRepository));
    }

    @Test
    void shouldUpdateNviPeriod() {
        var year = YEAR + 1;
        var originalRequest = createRequest(year, nowPlusDays(1), nowPlusDays(2));
        var originalPeriod = NviPeriod.upsert(originalRequest, periodRepository);
        var updatedRequest = updateRequest(year, originalRequest.startDate(), nowPlusDays(3));
        var updatedPeriod = NviPeriod.upsert(updatedRequest, periodRepository);
        assertNotEquals(updatedPeriod, originalPeriod);
    }

    @Test
    void shouldNotAllowNviPeriodReportingDateInInPast() {
        var year = YEAR + 1;
        var originalRequest = createRequest(year, Instant.now(), nowPlusDays(1));
        NviPeriod.upsert(originalRequest, periodRepository);
        var updatedRequest = updateRequest(year, originalRequest.startDate(),
                                           ZonedDateTime.now().minusWeeks(10).toInstant());
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.upsert(updatedRequest, periodRepository));
    }

    @Test
    void shouldNotAllowNviPeriodStartAfterReportingDate() {
        var year = YEAR + 1;
        var originalRequest = createRequest(year, Instant.now(), nowPlusDays(1));
        NviPeriod.upsert(originalRequest, periodRepository);
        var updatedRequest = updateRequest(year, nowPlusDays(2), originalRequest.reportingDate());
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.upsert(updatedRequest, periodRepository));
    }

    @Test
    void shouldReturnIllegalArgumentExceptionWhenPublishingYearHasInvalidLength() {
        var createRequest = createRequest(22);
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.upsert(createRequest, periodRepository));
    }

    @Test
    void shouldReturnIllegalArgumentExceptionWhenWhenStartDateHasAlreadyBeenReached() {
        var createRequest = createRequest(YEAR, Instant.now(), nowPlusDays(1));
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.upsert(createRequest, periodRepository));
    }

    @Test
    void shouldReturnIllegalArgumentWhenReportingDateIsBeforeNow() {
        var createRequest = createRequest(YEAR, nowPlusDays(1),
                                          Instant.now().minus(1, ChronoUnit.DAYS));
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.upsert(createRequest, periodRepository));
    }

    @Test
    void shouldReturnIllegalArgumentWhenStartDateIsAfterReportingDate() {
        var createRequest = createRequest(YEAR, nowPlusDays(10),
                                          nowPlusDays(9));
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.upsert(createRequest, periodRepository));
    }

    @Test
    void shouldReturnPeriodsOnlyWhenFetchingPeriods() {
        NviPeriod.upsert(createRequest(Year.now().getValue(), nowPlusDays(10),
                                       nowPlusOneYear()), periodRepository);
        NviPeriod.upsert(createRequest(Year.now().getValue(), nowPlusDays(10),
                                       nowPlusOneYear()), periodRepository);
        assertEquals(2, NviPeriod.fetchAll(periodRepository).size());
    }

    @Test
    void shouldThrowPeriodNotFoundExceptionWhenPeriodDoesNotExist() {
        assertThrows(PeriodNotFoundException.class, () -> new NviService(localDynamo).getPeriod(randomYear()));
    }

    private static Username randomUserName() {
        return new Username(randomString());
    }

    private static Instant nowPlusDays(int numberOfDays) {
        return Instant.now().plus(numberOfDays, ChronoUnit.DAYS);
    }

    private static Instant nowPlusOneYear() {
        return Instant.now().plus(1, ChronoUnit.YEARS);
    }

    private static CreatePeriodRequest createRequest(int year, Instant startDate, Instant reportingDate) {
        return UpsertPeriodRequest.builder()
                   .withPublishingYear(year)
                   .withStartDate(startDate)
                   .withReportingDate(reportingDate)
                   .buildCreateRequest();
    }

    private static UpdatePeriodRequest updateRequest(int year, Instant startDate, Instant reportingDate) {
        return UpsertPeriodRequest.builder()
                   .withPublishingYear(year)
                   .withStartDate(startDate)
                   .withReportingDate(reportingDate)
                   .buildUpdateRequest();
    }

    private static CreatePeriodRequest createRequest(int year) {
        var startDate = LocalDateTime.of(year, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);
        var reportingDate = LocalDateTime.of(year + 1, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);
        return createRequest(year, startDate, reportingDate);
    }

    private static URI constructExpectedId(CreatePeriodRequest createPeriodRequest) {
        return UriWrapper.fromHost(new Environment().readEnv("API_HOST"))
                   .addChild("scientific-index")
                   .addChild("period")
                   .addChild(String.valueOf(createPeriodRequest.publishingYear()))
                   .getUri();
    }

    private void setUpExtingPeriod() {
        periodRepository.save(DbNviPeriod.builder()
                                  .publishingYear(String.valueOf(YEAR))
                                  .startDate(LocalDateTime.of(YEAR, 4, 1, 0, 0).toInstant(ZoneOffset.UTC))
                                  .reportingDate(LocalDateTime.of(YEAR + 1, 3, 31, 23, 59).toInstant(ZoneOffset.UTC))
                                  .build());
    }
}