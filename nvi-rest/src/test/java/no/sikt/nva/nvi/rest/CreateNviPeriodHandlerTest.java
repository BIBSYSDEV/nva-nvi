package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.initializeTestDatabase;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.rest.create.CreateNviPeriodHandler;
import no.sikt.nva.nvi.rest.model.UpsertNviPeriodRequest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class CreateNviPeriodHandlerTest {

  private Context context;
  private ByteArrayOutputStream output;
  private CreateNviPeriodHandler handler;
  private PeriodRepository periodRepository;

  @BeforeEach
  void init() {
    output = new ByteArrayOutputStream();
    context = mock(Context.class);
    periodRepository = new PeriodRepository(initializeTestDatabase());
    handler = new CreateNviPeriodHandler(periodRepository);
  }

  @Test
  void shouldReturnUnauthorizedWhenMissingAccessRightsToOpenNviPeriod() throws IOException {
    handler.handleRequest(createRequestWithoutAccessRights(), output, context);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnBadRequestWhenInvalidReportingDate() throws IOException {
    var period = new UpsertNviPeriodRequest("2023", null, "invalidValue");
    handler.handleRequest(createRequest(period), output, context);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
  }

  @Test
  void shouldReturnBadRequestWhenPeriodExists() throws IOException {
    var year = String.valueOf(ZonedDateTime.now().getYear());
    setupFuturePeriod(year, periodRepository);
    var period = upsertRequest(year);
    handler.handleRequest(createRequest(period), output, context);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
  }

  @Test
  void shouldCreateNviPeriod() throws IOException {
    var year = String.valueOf(ZonedDateTime.now().getYear());
    var period = upsertRequest(year);
    handler.handleRequest(createRequest(period), output, context);
    var persistedPeriod = NviPeriod.fetchByPublishingYear(year, periodRepository);
    assertThat(
        period.publishingYear(), is(equalTo(persistedPeriod.getPublishingYear().toString())));
  }

  private InputStream createRequestWithoutAccessRights() throws JsonProcessingException {
    return new HandlerRequestBuilder<UpsertNviPeriodRequest>(JsonUtils.dtoObjectMapper)
        .withBody(upsertRequest(String.valueOf(ZonedDateTime.now().getYear())))
        .build();
  }

  private InputStream createRequest(UpsertNviPeriodRequest period) throws JsonProcessingException {
    return new HandlerRequestBuilder<UpsertNviPeriodRequest>(JsonUtils.dtoObjectMapper)
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
