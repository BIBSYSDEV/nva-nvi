package no.sikt.nva.nvi.rest;

import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.Matchers.is;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.Period;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.service.NviService;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class CreateNviPeriodHandlerTest {

    private Context context;
    private ByteArrayOutputStream output;
    private CreateNviPeriodHandler handler;
    private NviService nviService;

    @BeforeEach
    void init() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        nviService = mock(NviService.class);
        handler = new CreateNviPeriodHandler(nviService);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRightsToOpenNviPeriod() throws IOException {
        handler.handleRequest(createRequestWithoutAccessRights(), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnBadRequestWhenMissingAccessRightsToOpenNviPeriod() throws IOException {
        when(nviService.createPeriod(any())).thenThrow(IllegalArgumentException.class);
        handler.handleRequest(createRequest(), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    //TODO: Assert persisted period when nviService is implemented
    @Test
    void shouldCreateNviPeriod() throws IOException {
        when(nviService.createPeriod(any())).thenReturn(randomPeriod());
        handler.handleRequest(createRequest(), output, context);
        var response = GatewayResponse.fromOutputStream(output, Period.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CREATED)));
    }

    private InputStream createRequestWithoutAccessRights() throws JsonProcessingException {
        return new HandlerRequestBuilder<DbNviPeriod>(JsonUtils.dtoObjectMapper).withBody(randomPeriod()).build();
    }

    private InputStream createRequest() throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<DbNviPeriod>(JsonUtils.dtoObjectMapper)
                   .withBody(randomPeriod())
                   .withCurrentCustomer(customerId)
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_PERIODS.name())
                   .withUserName(randomString())
                   .build();
    }

    private DbNviPeriod randomPeriod() {
        var start = randomInstant();
        return DbNviPeriod.builder()
                   .reportingDate(start)
                   .publishingYear(String.valueOf(randomInteger(9999)))
                   .build();
    }
}
