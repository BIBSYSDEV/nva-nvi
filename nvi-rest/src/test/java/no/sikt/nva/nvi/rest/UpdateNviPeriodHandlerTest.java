package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupPersistedNotOpenedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import no.sikt.nva.nvi.common.LocalDynamoTestSetup;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.rest.model.UpsertNviPeriodRequest;
import no.sikt.nva.nvi.rest.upsert.UpdateNviPeriodHandler;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class UpdateNviPeriodHandlerTest extends LocalDynamoTestSetup {

  private Context context;
  private ByteArrayOutputStream output;
  private UpdateNviPeriodHandler handler;
  private PeriodRepository periodRepository;

  @BeforeEach
  void init() {
    output = new ByteArrayOutputStream();
    context = mock(Context.class);
    periodRepository = new PeriodRepository(initializeTestDatabase());
    handler = new UpdateNviPeriodHandler(periodRepository);
  }

  @Test
  void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
    handler.handleRequest(createRequestWithoutAccessRights(), output, context);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnNotFoundWhenPeriodDoesNotExists() throws IOException {
    handler.handleRequest(toInputStream(randomUpsertNviPeriodRequest()), output, context);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_NOT_FOUND)));
  }

  @Test
  void shouldUpdateNviPeriodSuccessfully() throws IOException {
    var year = String.valueOf(ZonedDateTime.now().getYear());
    var persistedPeriod = setupPersistedNotOpenedPeriod(year, periodRepository);
    var updateRequest = updateRequest(year, persistedPeriod);
    handler.handleRequest(toInputStream(updateRequest), output, context);
    var updatedPeriod = NviPeriod.fetchByPublishingYear(year, periodRepository);

    assertThat(
        persistedPeriod.getReportingDate(), is(not(equalTo(updatedPeriod.getReportingDate()))));
  }

  private UpsertNviPeriodRequest updateRequest(String year, NviPeriod persistedPeriod) {
    return new UpsertNviPeriodRequest(
        year,
        persistedPeriod.getStartDate().plus(1, ChronoUnit.DAYS).toString(),
        persistedPeriod.getReportingDate().plus(1, ChronoUnit.DAYS).toString());
  }

  private UpsertNviPeriodRequest randomUpsertNviPeriodRequest() {
    return new UpsertNviPeriodRequest(
        String.valueOf(ZonedDateTime.now().getYear()),
        ZonedDateTime.now().plusMonths(1).toInstant().toString(),
        ZonedDateTime.now().plusMonths(10).toInstant().toString());
  }

  private InputStream toInputStream(UpsertNviPeriodRequest request) throws JsonProcessingException {
    return new HandlerRequestBuilder<UpsertNviPeriodRequest>(JsonUtils.dtoObjectMapper)
        .withBody(request)
        .withAccessRights(randomUri(), AccessRight.MANAGE_NVI)
        .withUserName(randomString())
        .build();
  }

  private InputStream createRequestWithoutAccessRights() throws JsonProcessingException {
    return new HandlerRequestBuilder<UpsertNviPeriodRequest>(JsonUtils.dtoObjectMapper)
        .withBody(randomUpsertNviPeriodRequest())
        .build();
  }
}
