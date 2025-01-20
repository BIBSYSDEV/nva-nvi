package no.sikt.nva.nvi.common.service.model;

import static java.time.temporal.ChronoUnit.DAYS;
import static no.sikt.nva.nvi.test.TestUtils.randomUserName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.exception.PeriodAlreadyExistsException;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NviPeriodTest extends LocalDynamoTest {

  private static final int YEAR = LocalDateTime.now().getYear() + 1;
  private PeriodRepository periodRepository;

  @BeforeEach
  void setup() {
    localDynamo = initializeTestDatabase();
    periodRepository = new PeriodRepository(localDynamo);
  }

  @Test
  void shouldCreateNviPeriod() {
    var request = createRequest(YEAR);
    var expectedId = constructExpectedId(request);
    var actual = NviPeriod.create(request, periodRepository);
    assertEquals(expectedId, actual.getId());
    assertThatRequestMatchesYear(request, actual);
  }

  @Test
  void shouldCreateNviPeriodForCurrentYear() {
    var request = createRequest(YEAR - 1);
    var expectedId = constructExpectedId(request);
    var actual = NviPeriod.create(request, periodRepository);
    assertEquals(expectedId, actual.getId());
    assertThatRequestMatchesYear(request, actual);
  }

  @Test
  void shouldThrowPeriodAlreadyExistsExceptionWhenPeriodAlreadyExists() {
    var createRequest = createRequest(YEAR);
    NviPeriod.create(createRequest, periodRepository);
    var newCreateRequest = createRequest(YEAR);
    assertThrows(
        PeriodAlreadyExistsException.class,
        () -> NviPeriod.create(newCreateRequest, periodRepository));
  }

  @Test
  void shouldThrowIllegalArgumentWhenPeriodMissedMandatoryValues() {
    var createPeriodRequest =
        CreatePeriodRequest.builder()
            .withPublishingYear(YEAR)
            .withCreatedBy(randomUserName())
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> NviPeriod.create(createPeriodRequest, periodRepository));
  }

  @Test
  void shouldUpdateNviPeriod() {
    var originalRequest = createRequest(YEAR, nowPlusDays(1), nowPlusDays(2));
    var originalPeriod = NviPeriod.create(originalRequest, periodRepository);
    var updatedRequest = updateRequest(YEAR, originalRequest.startDate(), nowPlusDays(3));
    var updatedPeriod = NviPeriod.update(updatedRequest, periodRepository);
    assertNotEquals(updatedPeriod, originalPeriod);
    assertEquals(updatedRequest.modifiedBy(), updatedPeriod.getModifiedBy());
  }

  @Test
  void shouldNotAllowNviPeriodReportingDateInInPastOnUpdate() {
    var originalRequest = createRequest(YEAR, nowPlusDays(1), nowPlusDays(2));
    NviPeriod.create(originalRequest, periodRepository);
    var updatedRequest =
        updateRequest(
            YEAR, originalRequest.startDate(), ZonedDateTime.now().minusWeeks(10).toInstant());
    assertThrows(
        IllegalArgumentException.class, () -> NviPeriod.update(updatedRequest, periodRepository));
  }

  @Test
  void shouldNotAllowNviPeriodStartAfterReportingDateOnUpdate() {
    var originalRequest = createRequest(YEAR, nowPlusDays(1), nowPlusDays(2));
    NviPeriod.create(originalRequest, periodRepository);
    var updatedRequest =
        updateRequest(
            YEAR, originalRequest.reportingDate().plus(1, DAYS), originalRequest.reportingDate());
    assertThrows(
        IllegalArgumentException.class, () -> NviPeriod.update(updatedRequest, periodRepository));
  }

  @Test
  void shouldReturnIllegalArgumentExceptionWhenPublishingYearHasInvalidLength() {
    var createRequest = createRequest(22);
    assertThrows(
        IllegalArgumentException.class, () -> NviPeriod.create(createRequest, periodRepository));
  }

  @Test
  void shouldReturnIllegalArgumentWhenReportingDateIsBeforeNow() {
    var createRequest = createRequest(YEAR, nowPlusDays(1), Instant.now().minus(1, DAYS));
    assertThrows(
        IllegalArgumentException.class, () -> NviPeriod.create(createRequest, periodRepository));
  }

  @Test
  void shouldReturnIllegalArgumentWhenStartDateIsAfterReportingDate() {
    var createRequest = createRequest(YEAR, nowPlusDays(10), nowPlusDays(9));
    assertThrows(
        IllegalArgumentException.class, () -> NviPeriod.create(createRequest, periodRepository));
  }

  @Test
  void shouldThrowPeriodNotFoundExceptionWhenPeriodDoesNotExist() {
    assertThrows(
        PeriodNotFoundException.class,
        () -> NviPeriod.fetchByPublishingYear("2022", periodRepository));
  }

  @Test
  void shouldReturnDto() {
    var request = createRequest(YEAR);
    var expectedId = constructExpectedId(request);
    var actual = NviPeriod.create(request, periodRepository).toDto();
    assertEquals(expectedId, actual.id());
    assertEquals(request.publishingYear().toString(), actual.publishingYear());
    assertEquals(request.startDate().toString(), actual.startDate());
    assertEquals(request.reportingDate().toString(), actual.reportingDate());
  }

  private static Instant nowPlusDays(int numberOfDays) {
    return Instant.now().plus(numberOfDays, DAYS);
  }

  private static CreatePeriodRequest createRequest(
      int year, Instant startDate, Instant reportingDate) {
    return CreatePeriodRequest.builder()
        .withPublishingYear(year)
        .withStartDate(startDate)
        .withReportingDate(reportingDate)
        .withCreatedBy(randomUserName())
        .build();
  }

  private static UpdatePeriodRequest updateRequest(
      int year, Instant startDate, Instant reportingDate) {
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

  private static void assertThatRequestMatchesYear(CreatePeriodRequest request, NviPeriod actual) {
    assertEquals(request.publishingYear(), actual.getPublishingYear());
    assertEquals(request.startDate(), actual.getStartDate());
    assertEquals(request.reportingDate(), actual.getReportingDate());
    assertEquals(request.createdBy(), actual.getCreatedBy());
  }
}
