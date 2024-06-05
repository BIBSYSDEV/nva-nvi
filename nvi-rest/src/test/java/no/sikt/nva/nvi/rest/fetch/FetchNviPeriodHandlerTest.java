package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.test.TestUtils.randomUsername;
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
import java.net.URI;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.model.UpsertNviPeriodRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;
import software.amazon.awssdk.utils.MapUtils;

class FetchNviPeriodHandlerTest extends LocalDynamoTest {


    private Context context;
    private ByteArrayOutputStream output;
    private FetchNviPeriodHandler handler;
    private NviService nviService;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        context = new FakeContext();
        nviService = new NviService(initializeTestDatabase());
        handler = new FetchNviPeriodHandler(nviService);
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
        var periodYear = createPeriod(String.valueOf(ZonedDateTime.now().getYear()));
        nviService.createPeriod(periodYear);
        handler.handleRequest(createRequestForPeriod(periodYear.publishingYear()), output, context);
        var response = GatewayResponse.fromOutputStream(output, UpsertNviPeriodRequest.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
        assertThat(response.getBodyObject(UpsertNviPeriodRequest.class), is(equalTo(
            UpsertNviPeriodRequest.fromNviPeriod(periodYear))));
    }

    private static DbNviPeriod createPeriod(String publishingYear) {
        return DbNviPeriod.builder()
                   .id(createId(publishingYear))
                   .publishingYear(publishingYear)
                   .startDate(ZonedDateTime.now().plusMonths(1).toInstant())
                   .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant())
                   .createdBy(randomUsername())
                   .build();
    }

    private static URI createId(String publishingYear) {
        return UriWrapper.fromHost(new Environment().readEnv("API_HOST"))
                   .addChild("scientific-index")
                   .addChild("period")
                   .addChild(publishingYear)
                   .getUri();
    }

    private InputStream createRequestForPeriod(String period) throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withPathParameters(MapUtils.of("periodIdentifier", period))
                   .build();
    }
}