package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.rest.model.NviPeriodsResponse;
import no.sikt.nva.nvi.rest.model.UpsertNviPeriodRequest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FetchNviPeriodsHandlerTest {

  private Context context;
  private ByteArrayOutputStream output;
  private FetchNviPeriodsHandler handler;
  private TestScenario scenario;

  @BeforeEach
  public void setUp() {
    scenario = new TestScenario();
    output = new ByteArrayOutputStream();
    context = new FakeContext();
    handler =
        new FetchNviPeriodsHandler(
            new NviPeriodService(scenario.getPeriodRepository()), new Environment());
  }

  @Test
  void shouldThrowUnauthorizedWhenMissingAccessRights() throws IOException {
    handler.handleRequest(
        createRequestWithAccessRight(AccessRight.MANAGE_PUBLISHING_REQUESTS), output, context);
    var response = GatewayResponse.fromOutputStream(output, NviPeriodsResponse.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnPeriodsWhenUserHasAccessRights() throws IOException {
    setupFuturePeriod(scenario, CURRENT_YEAR + 1);
    setupFuturePeriod(scenario, CURRENT_YEAR + 2);
    handler.handleRequest(createRequestWithAccessRight(AccessRight.MANAGE_NVI), output, context);
    var response = GatewayResponse.fromOutputStream(output, NviPeriodsResponse.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
    assertThat(response.getBodyObject(NviPeriodsResponse.class).periods(), hasSize(2));
  }

  @Test
  void shouldReturnSuccessWhenThereIsNoPeriods() throws IOException {
    handler.handleRequest(createRequestWithAccessRight(AccessRight.MANAGE_NVI), output, context);
    var response = GatewayResponse.fromOutputStream(output, NviPeriodsResponse.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
    assertThat(response.getBodyObject(NviPeriodsResponse.class).periods(), hasSize(0));
  }

  private InputStream createRequestWithAccessRight(AccessRight accessRight)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<UpsertNviPeriodRequest>(JsonUtils.dtoObjectMapper)
        .withAccessRights(randomUri(), accessRight)
        .withUserName(randomString())
        .build();
  }
}
