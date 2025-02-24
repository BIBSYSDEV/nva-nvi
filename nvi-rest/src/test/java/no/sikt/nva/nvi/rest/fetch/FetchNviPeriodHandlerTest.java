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
import no.sikt.nva.nvi.common.LocalDynamoTestSetup;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;
import software.amazon.awssdk.utils.MapUtils;

class FetchNviPeriodHandlerTest extends LocalDynamoTestSetup {

  private Context context;
  private ByteArrayOutputStream output;
  private FetchNviPeriodHandler handler;
  private PeriodRepository periodRepository;

  @BeforeEach
  public void setUp() {
    output = new ByteArrayOutputStream();
    context = new FakeContext();
    periodRepository = new PeriodRepository(initializeTestDatabase());
    handler = new FetchNviPeriodHandler(periodRepository);
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
    var expectedPeriod = setupFuturePeriod(publishingYear, periodRepository).toDto();
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
