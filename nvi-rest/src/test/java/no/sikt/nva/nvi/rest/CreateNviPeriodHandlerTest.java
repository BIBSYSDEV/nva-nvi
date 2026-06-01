package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.rest.EnvironmentFixtures.CREATE_NVI_PERIOD_HANDLER;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.exceptions.TransactionException;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.NviPeriodServiceThrowingTransactionExceptions;
import no.sikt.nva.nvi.rest.create.CreateNviPeriodHandler;
import no.sikt.nva.nvi.rest.model.UpsertNviPeriodRequest;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class CreateNviPeriodHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  private ByteArrayOutputStream output;
  private CreateNviPeriodHandler handler;
  private TestScenario scenario;
  private NviPeriodService periodService;

  @BeforeEach
  void init() {
    scenario = new TestScenario();
    output = new ByteArrayOutputStream();

    periodService = new NviPeriodService(CREATE_NVI_PERIOD_HANDLER, scenario.getPeriodRepository());
    handler = new CreateNviPeriodHandler(periodService, CREATE_NVI_PERIOD_HANDLER);
  }

  @Test
  void shouldReturnUnauthorizedWhenMissingAccessRightsToOpenNviPeriod() throws IOException {
    handler.handleRequest(createRequestWithoutAccessRights(), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, response.getStatusCode());
  }

  @Test
  void shouldReturnBadRequestWhenInvalidReportingDate() throws IOException {
    var period = new UpsertNviPeriodRequest("2023", null, "invalidValue");
    handler.handleRequest(createRequest(period), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getStatusCode());
  }

  @Test
  void shouldReturnBadRequestWhenPeriodExists() throws IOException {
    var year = String.valueOf(CURRENT_YEAR);
    setupFuturePeriod(scenario, year);
    var period = upsertRequest(year);
    handler.handleRequest(createRequest(period), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getStatusCode());
  }

  @Test
  void shouldCreateNviPeriod() throws IOException {
    var year = String.valueOf(CURRENT_YEAR);
    var period = upsertRequest(year);
    handler.handleRequest(createRequest(period), output, CONTEXT);
    var persistedPeriod = periodService.getByPublishingYear(year);
    assertEquals(persistedPeriod.publishingYear().toString(), period.publishingYear());
  }

  @Test
  void shouldReturnConflictErrorWhenTransactionFailsDueToConflict() throws IOException {
    var year = String.valueOf(CURRENT_YEAR);
    var input = createRequest(upsertRequest(year));

    var failingHandler = setupHandlerThatFailsWithTransactionConflict();
    var response = handleRequestExpectingProblem(failingHandler, input);

    Assertions.assertThat(response.getStatus().getStatusCode())
        .isEqualTo(HttpURLConnection.HTTP_CONFLICT);
    Assertions.assertThat(response.getDetail()).isEqualTo(TransactionException.USER_MESSAGE);
  }

  private CreateNviPeriodHandler setupHandlerThatFailsWithTransactionConflict() {
    return new CreateNviPeriodHandler(
        new NviPeriodServiceThrowingTransactionExceptions(
            CREATE_NVI_PERIOD_HANDLER, scenario.getPeriodRepository()),
        CREATE_NVI_PERIOD_HANDLER);
  }

  private Problem handleRequestExpectingProblem(
      CreateNviPeriodHandler handlerUnderTest, InputStream input) {
    try {
      handlerUnderTest.handleRequest(input, output, CONTEXT);
      var response = GatewayResponse.fromOutputStream(output, Problem.class);
      return dtoObjectMapper.readValue(response.getBody(), Problem.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream createRequestWithoutAccessRights() throws JsonProcessingException {
    return new HandlerRequestBuilder<UpsertNviPeriodRequest>(dtoObjectMapper)
        .withBody(upsertRequest(THIS_YEAR))
        .build();
  }

  private InputStream createRequest(UpsertNviPeriodRequest period) throws JsonProcessingException {
    return new HandlerRequestBuilder<UpsertNviPeriodRequest>(dtoObjectMapper)
        .withBody(period)
        .withAccessRights(randomUri(), AccessRight.MANAGE_NVI)
        .withUserName(randomString())
        .build();
  }

  private UpsertNviPeriodRequest upsertRequest(String year) {
    return new UpsertNviPeriodRequest(
        year,
        ZonedDateTime.now().plusMonths(1).toInstant().toString(),
        ZonedDateTime.now().plusMonths(10).toInstant().toString());
  }
}
