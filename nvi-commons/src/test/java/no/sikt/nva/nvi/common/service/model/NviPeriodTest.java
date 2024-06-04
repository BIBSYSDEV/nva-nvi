package no.sikt.nva.nvi.common.service.model;

import static java.time.temporal.ChronoUnit.DAYS;
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
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.exceptions.MethodNotAllowedException;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NviPeriodTest extends LocalDynamoTest {

    private static final int YEAR = LocalDateTime.now().getYear();
    private PeriodRepository periodRepository;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        periodRepository = new PeriodRepository(localDynamo);
        setUpExistingPeriod();
    }

    @Test
    void shouldCreateNviPeriod() {
        var request = createRequest(YEAR + 1);
        var expectedId = constructExpectedId(request);
        var expected = new NviPeriod(expectedId, request.publishingYear(), request.startDate(),
                                     request.reportingDate(), request.createdBy(), null);
        var actual = NviPeriod.create(request, periodRepository);
        assertEquals(expected, actual);
    }

    @Test
    void shouldThrowMethodNotAllowedExceptionWhenPeriodAlreadyExists() {
        var createRequest = createRequest(YEAR + 1);
        NviPeriod.create(createRequest, periodRepository);
        var newCreateRequest = createRequest(YEAR + 1);
        assertThrows(MethodNotAllowedException.class, () -> NviPeriod.create(createRequest, periodRepository));
    }

    @Test
    void shouldThrowIllegalArgumentWhenPeriodMissedMandatoryValues() {
        var createPeriodRequest = CreatePeriodRequest.builder()
                                      .withPublishingYear(YEAR + 1)
                                      .withCreatedBy(randomUserName())
                                      .build();
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.create(createPeriodRequest, periodRepository));
    }

    @Test
    void shouldUpdateNviPeriod() {
        var year = YEAR + 1;
        var originalRequest = createRequest(year, nowPlusDays(1), nowPlusDays(2));
        var originalPeriod = NviPeriod.create(originalRequest, periodRepository);
        var updatedRequest = updateRequest(year, originalRequest.startDate(), nowPlusDays(3));
        var updatedPeriod = NviPeriod.update(updatedRequest, periodRepository);
        assertNotEquals(updatedPeriod, originalPeriod);
    }

    @Test
    void shouldNotAllowNviPeriodReportingDateInInPastOnUpdate() {
        var year = YEAR + 1;
        var originalRequest = createRequest(year, nowPlusDays(1), nowPlusDays(2));
        NviPeriod.create(originalRequest, periodRepository);
        var updatedRequest = updateRequest(year, originalRequest.startDate(),
                                           ZonedDateTime.now().minusWeeks(10).toInstant());
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.update(updatedRequest, periodRepository));
    }

    @Test
    void shouldNotAllowNviPeriodStartAfterReportingDateOnUpdate() {
        var year = YEAR + 1;
        var originalRequest = createRequest(year, nowPlusDays(1), nowPlusDays(2));
        NviPeriod.create(originalRequest, periodRepository);
        var updatedRequest = updateRequest(year, originalRequest.reportingDate().plus(1, DAYS),
                                           originalRequest.reportingDate());
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.update(updatedRequest, periodRepository));
    }

    @Test
    void shouldReturnIllegalArgumentExceptionWhenPublishingYearHasInvalidLength() {
        var createRequest = createRequest(22);
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.create(createRequest, periodRepository));
    }

    @Test
    void shouldReturnIllegalArgumentExceptionWhenWhenStartDateHasAlreadyBeenReached() {
        var createRequest = createRequest(YEAR, Instant.now(), nowPlusDays(1));
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.create(createRequest, periodRepository));
    }

    @Test
    void shouldReturnIllegalArgumentWhenReportingDateIsBeforeNow() {
        var createRequest = createRequest(YEAR, nowPlusDays(1),
                                          Instant.now().minus(1, DAYS));
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.create(createRequest, periodRepository));
    }

    @Test
    void shouldReturnIllegalArgumentWhenStartDateIsAfterReportingDate() {
        var createRequest = createRequest(YEAR, nowPlusDays(10),
                                          nowPlusDays(9));
        assertThrows(IllegalArgumentException.class, () -> NviPeriod.create(createRequest, periodRepository));
    }

    @Test
    void shouldReturnPeriodsOnlyWhenFetchingPeriods() {
        NviPeriod.create(createRequest(Year.now().getValue(), nowPlusDays(10),
                                       nowPlusOneYear()), periodRepository);
        NviPeriod.create(createRequest(Year.now().getValue() + 1, nowPlusDays(10),
                                       nowPlusOneYear()), periodRepository);
        assertEquals(2, NviPeriod.fetchAll(periodRepository).size());
    }

    @Test
    void shouldThrowPeriodNotFoundExceptionWhenPeriodDoesNotExist() {
        assertThrows(PeriodNotFoundException.class, () -> NviPeriod.fetch("2022", periodRepository));
    }

    private static Username randomUserName() {
        return new Username(randomString());
    }

    private static Instant nowPlusDays(int numberOfDays) {
        return Instant.now().plus(numberOfDays, DAYS);
    }

    private static Instant nowPlusOneYear() {
        return Instant.now().plus(365, DAYS);
    }

    private static CreatePeriodRequest createRequest(int year, Instant startDate, Instant reportingDate) {
        return CreatePeriodRequest.builder()
                   .withPublishingYear(year)
                   .withStartDate(startDate)
                   .withReportingDate(reportingDate)
                   .withCreatedBy(randomUserName())
                   .build();
    }

    private static UpdatePeriodRequest updateRequest(int year, Instant startDate, Instant reportingDate) {
        return UpdatePeriodRequest.builder()
                   .withPublishingYear(year)
                   .withStartDate(startDate)
                   .withReportingDate(reportingDate)
                   .withModifiedBy(randomUserName())
                   .build();
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

    private void setUpExistingPeriod() {
        periodRepository.save(DbNviPeriod.builder()
                                  .publishingYear(String.valueOf(YEAR))
                                  .startDate(LocalDateTime.of(YEAR, 4, 1, 0, 0).toInstant(ZoneOffset.UTC))
                                  .reportingDate(LocalDateTime.of(YEAR + 1, 3, 31, 23, 59).toInstant(ZoneOffset.UTC))
                                  .build());
    }
}