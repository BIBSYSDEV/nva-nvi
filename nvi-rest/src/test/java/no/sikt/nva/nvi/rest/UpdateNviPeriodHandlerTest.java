package no.sikt.nva.nvi.rest;

import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import no.sikt.nva.nvi.common.model.business.NviPeriod;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.model.NviPeriodDto;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.ConflictException;
import nva.commons.apigateway.exceptions.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class UpdateNviPeriodHandlerTest extends LocalDynamoTest {

    private Context context;
    private ByteArrayOutputStream output;
    private UpdateNviPeriodHandler handler;
    private NviService nviService;

    @BeforeEach
    void init() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        nviService = new NviService((initializeTestDatabase()));;
        handler = new UpdateNviPeriodHandler(nviService);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(createRequestWithoutAccessRights(), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnNotFoundWhenPeriodDoesNotExists()
        throws IOException {
        var period = randomPeriod();
        handler.handleRequest(createRequest(period), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_NOT_FOUND)));
    }

    @Test
    void shouldReturnConflictWhenUpdatingReportingDateNotSupportedDate()
        throws NotFoundException, IOException, ConflictException, BadRequestException {
        when(nviService.updatePeriod(any())).thenThrow(ConflictException.class);
        handler.handleRequest(createRequest(), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    @Test
    void shouldUpdateNviPeriodSuccessfully()
        throws IOException, BadRequestException {
        var persistedPeriod = nviService.createPeriod(randomPeriod());
        var newValue = persistedPeriod.copy().withReportingDate(new Date(2050,0,25).toInstant()).build();
        handler.handleRequest(createRequest(newValue), output, context);
        var updatedPeriod = nviService.getPeriod(persistedPeriod.publishingYear());

        assertThat(persistedPeriod.reportingDate(), is(not(equalTo(updatedPeriod.reportingDate()))));

    }

    private InputStream createRequest(NviPeriod period) throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<DbNviPeriod>(JsonUtils.dtoObjectMapper).withBody(randomPeriod())
                   .withCurrentCustomer(customerId)
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_PERIODS.name())
                   .withUserName(randomString())
                   .withBody(period)
                   .build();
    }

    private InputStream createRequestWithoutAccessRights() throws JsonProcessingException {
        return new HandlerRequestBuilder<DbNviPeriod>(JsonUtils.dtoObjectMapper).withBody(randomPeriod()).build();
    }

    private NviPeriod randomPeriod() {
        return new NviPeriod.Builder()
                   .withReportingDate(new Date(2050,03,25).toInstant())
                   .withPublishingYear(String.valueOf(2023))
                   .withCreatedBy(new Username(randomString()))
                   .build();
    }
}
