package no.sikt.nva.nvi.common.service.model;

import static java.time.temporal.ChronoUnit.DAYS;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getGlobalEnvironment;
import static no.sikt.nva.nvi.common.model.UsernameFixtures.randomUsername;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.exception.PeriodAlreadyExistsException;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NviPeriodTest {

  private static final int YEAR = LocalDateTime.now().getYear() + 1;
  private static final Environment ENVIRONMENT = getGlobalEnvironment();
  private NviPeriodService periodService;

  @BeforeEach
  void setup() {
    var scenario = new TestScenario();
    periodService = new NviPeriodService(ENVIRONMENT, scenario.getPeriodRepository());
  }

  @Test
  void shouldCreateNviPeriod() {
    var request = createRequest(YEAR);
    var expectedId = constructExpectedId(request);
    var actual = periodService.create(request);
    assertEquals(expectedId, actual.id());
    assertThatRequestMatchesYear(request, actual);
  }

  @Test
  void shouldCreateNviPeriodForCurrentYear() {
    var request = createRequest(YEAR - 1);
    var expectedId = constructExpectedId(request);
    var actual = periodService.create(request);
    assertEquals(expectedId, actual.id());
    assertThatRequestMatchesYear(request, actual);
  }

  @Test
  void shouldThrowPeriodAlreadyExistsExceptionWhenPeriodAlreadyExists() {
    var createRequest = createRequest(YEAR);
    periodService.create(createRequest);
    var newCreateRequest = createRequest(YEAR);
    assertThrows(PeriodAlreadyExistsException.class, () -> periodService.create(newCreateRequest));
  }

  @Test
  void shouldThrowIllegalArgumentWhenPeriodMissedMandatoryValues() {
    var createPeriodRequest =
        CreatePeriodRequest.builder()
            .withPublishingYear(YEAR)
            .withCreatedBy(randomUsername())
            .build();
    assertThrows(IllegalArgumentException.class, () -> periodService.create(createPeriodRequest));
  }

  @Test
  void shouldUpdateNviPeriod() {
    var originalRequest = createRequest(YEAR, nowPlusDays(1), nowPlusDays(2));
    var originalPeriod = periodService.create(originalRequest);
    var updatedRequest = updateRequest(YEAR, originalRequest.startDate(), nowPlusDays(3));
    var updatedPeriod = periodService.update(updatedRequest);
    assertNotEquals(updatedPeriod, originalPeriod);
    assertEquals(updatedRequest.modifiedBy(), updatedPeriod.modifiedBy());
  }

  @Test
  void shouldNotAllowNviPeriodReportingDateInInPastOnUpdate() {
    var originalRequest = createRequest(YEAR, nowPlusDays(1), nowPlusDays(2));
    periodService.create(originalRequest);
    var updatedRequest =
        updateRequest(
            YEAR, originalRequest.startDate(), ZonedDateTime.now().minusWeeks(10).toInstant());
    assertThrows(IllegalArgumentException.class, () -> periodService.update(updatedRequest));
  }

  @Test
  void shouldNotAllowNviPeriodStartAfterReportingDateOnUpdate() {
    var originalRequest = createRequest(YEAR, nowPlusDays(1), nowPlusDays(2));
    periodService.create(originalRequest);
    var updatedRequest =
        updateRequest(
            YEAR, originalRequest.reportingDate().plus(1, DAYS), originalRequest.reportingDate());
    assertThrows(IllegalArgumentException.class, () -> periodService.update(updatedRequest));
  }

  @Test
  void shouldReturnIllegalArgumentExceptionWhenPublishingYearHasInvalidLength() {
    var createRequest = createRequest(22);
    assertThrows(IllegalArgumentException.class, () -> periodService.create(createRequest));
  }

  @Test
  void shouldReturnIllegalArgumentWhenReportingDateIsBeforeNow() {
    var createRequest = createRequest(YEAR, nowPlusDays(1), Instant.now().minus(1, DAYS));
    assertThrows(IllegalArgumentException.class, () -> periodService.create(createRequest));
  }

  @Test
  void shouldReturnIllegalArgumentWhenStartDateIsAfterReportingDate() {
    var createRequest = createRequest(YEAR, nowPlusDays(10), nowPlusDays(9));
    assertThrows(IllegalArgumentException.class, () -> periodService.create(createRequest));
  }

  @Test
  void shouldThrowPeriodNotFoundExceptionWhenPeriodDoesNotExist() {
    assertThrows(PeriodNotFoundException.class, () -> periodService.getByPublishingYear("2022"));
  }

  @Test
  void shouldReturnDto() {
    var request = createRequest(YEAR);
    var expectedId = constructExpectedId(request);
    var actual = periodService.create(request).toDto();
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
        .withCreatedBy(randomUsername())
        .build();
  }

  private static UpdatePeriodRequest updateRequest(
      int year, Instant startDate, Instant reportingDate) {
    return UpdatePeriodRequest.builder()
        .withPublishingYear(year)
        .withStartDate(startDate)
        .withReportingDate(reportingDate)
        .withModifiedBy(randomUsername())
        .build();
  }

  private static CreatePeriodRequest createRequest(int year) {
    var startDate = LocalDateTime.of(year, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);
    var reportingDate = LocalDateTime.of(year + 1, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);
    return createRequest(year, startDate, reportingDate);
  }

  private static URI constructExpectedId(CreatePeriodRequest createPeriodRequest) {
    return UriWrapper.fromHost(ENVIRONMENT.readEnv("API_HOST"))
        .addChild("scientific-index")
        .addChild("period")
        .addChild(String.valueOf(createPeriodRequest.publishingYear()))
        .getUri();
  }

  private static void assertThatRequestMatchesYear(CreatePeriodRequest request, NviPeriod actual) {
    assertEquals(request.publishingYear(), actual.publishingYear());
    assertEquals(request.startDate(), actual.startDate());
    assertEquals(request.reportingDate(), actual.reportingDate());
    assertEquals(request.createdBy(), actual.createdBy());
  }
}
