package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;
import software.amazon.awssdk.utils.MapUtils;

class FetchNviPeriodHandlerTest {

  private Context context;
  private ByteArrayOutputStream output;
  private FetchNviPeriodHandler handler;
  private TestScenario scenario;

  @BeforeEach
  public void setUp() {
    scenario = new TestScenario();
    output = new ByteArrayOutputStream();
    context = new FakeContext();
    handler = new FetchNviPeriodHandler(scenario.getPeriodRepository(), new Environment());
  }

  @Test
  void shouldReturnNotFoundWhenPeriodDoesNotExist() throws IOException {
    var periodYear = randomString();
    handler.handleRequest(createRequestForPeriod(periodYear), output, context);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    var expectedMessage = String.format("Period for year %s does not exist!", periodYear);

    assertThat(response.getBodyObject(Problem.class).getDetail(), is(equalTo(expectedMessage)));
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_NOT_FOUND)));
  }

  @Test
  void shouldReturnPeriodSuccessfully() throws IOException {
    var publishingYear = String.valueOf(ZonedDateTime.now().getYear());
    var expectedPeriod = setupFuturePeriod(scenario, publishingYear).toDto();
    handler.handleRequest(createRequestForPeriod(publishingYear), output, context);
    var response = GatewayResponse.fromOutputStream(output, NviPeriodDto.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
    assertThat(response.getBodyObject(NviPeriodDto.class), is(equalTo(expectedPeriod)));
  }

  private InputStream createRequestForPeriod(String period) throws JsonProcessingException {
    return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
        .withPathParameters(MapUtils.of("periodIdentifier", period))
        .build();
  }
}
