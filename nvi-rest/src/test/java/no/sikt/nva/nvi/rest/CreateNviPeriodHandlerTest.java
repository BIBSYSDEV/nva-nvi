package no.sikt.nva.nvi.rest;

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
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.create.CreateNviPeriodHandler;
import no.sikt.nva.nvi.rest.model.NviPeriodDto;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class CreateNviPeriodHandlerTest extends LocalDynamoTest {

    private Context context;
    private ByteArrayOutputStream output;
    private CreateNviPeriodHandler handler;
    private NviService nviService;

    @BeforeEach
    void init() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        nviService = new NviService((initializeTestDatabase()));
        handler = new CreateNviPeriodHandler(nviService);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRightsToOpenNviPeriod() throws IOException {
        handler.handleRequest(createRequestWithoutAccessRights(), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnBadRequestWhenInvalidReportingDate() throws IOException {
        var period = new NviPeriodDto(randomUri(), "2023", null,"invalidValue");
        handler.handleRequest(createRequest(period), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    @Test
    void shouldCreateNviPeriod() throws IOException {
        var year = String.valueOf(ZonedDateTime.now().getYear());
        var period = randomPeriod(year);
        handler.handleRequest(createRequest(period), output, context);
        var persistedPeriod = nviService.getPeriod(year);
        assertThat(period.publishingYear(), is(equalTo(persistedPeriod.publishingYear())));
    }

    private InputStream createRequestWithoutAccessRights() throws JsonProcessingException {
        return new HandlerRequestBuilder<NviPeriodDto>(JsonUtils.dtoObjectMapper)
                   .withBody(randomPeriod(String.valueOf(ZonedDateTime.now().getYear()))).build();
    }

    private InputStream createRequest(NviPeriodDto period) throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<NviPeriodDto>(JsonUtils.dtoObjectMapper)
                   .withBody(period)
                   .withCurrentCustomer(customerId)
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI)
                   .withUserName(randomString())
                   .build();
    }

    private NviPeriodDto randomPeriod(String year) {
        return new NviPeriodDto(randomUri(),
                                year,
                                ZonedDateTime.now().plusMonths(1).toInstant().toString(),
                                ZonedDateTime.now().plusMonths(10).toInstant().toString());
    }
}
