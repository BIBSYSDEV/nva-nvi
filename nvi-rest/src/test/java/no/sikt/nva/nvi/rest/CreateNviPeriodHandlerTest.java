package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.rest.model.NviPeriodDto.INVALID_REPORTING_DATE_MESSAGE;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Date;
import no.sikt.nva.nvi.common.service.NviService;
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
    void shouldReturnBadRequestWhenReportingDateIsNull() throws IOException {
        var period = new NviPeriodDto("2023", null);
        handler.handleRequest(createRequest(period), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(response.getBody(), containsString(INVALID_REPORTING_DATE_MESSAGE));
    }

    @Test
    void shouldReturnBadRequestWhenReportingDateIsInvalid() throws IOException {
        var period = new NviPeriodDto("2023","invalid");
        handler.handleRequest(createRequest(period), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(response.getBody(), containsString(INVALID_REPORTING_DATE_MESSAGE));
    }

    @Test
    void shouldCreateNviPeriod() throws IOException {
        var period = validPeriod();
        handler.handleRequest(createRequest(period), output, context);
        var persistedPeriod = nviService.getPeriod("2023");
        assertThat(period.publishingYear(), is(equalTo(persistedPeriod.publishingYear())));
    }

    private static NviPeriodDto validPeriod() {
        return new NviPeriodDto("2023", new Date(2050, 03, 25).toInstant().toString());
    }

    private InputStream createRequestWithoutAccessRights() throws JsonProcessingException {
        return new HandlerRequestBuilder<NviPeriodDto>(JsonUtils.dtoObjectMapper).withBody(validPeriod()).build();
    }

    private InputStream createRequest(NviPeriodDto period) throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<NviPeriodDto>(JsonUtils.dtoObjectMapper)
                   .withBody(period)
                   .withCurrentCustomer(customerId)
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_PERIODS.name())
                   .withUserName(randomString())
                   .build();
    }
}
