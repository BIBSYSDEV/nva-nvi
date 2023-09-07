package no.sikt.nva.nvi.rest;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.not;
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
import java.util.Date;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.apigateway.exceptions.BadRequestException;
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
        nviService = new NviService((initializeTestDatabase()));
        ;
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
    void shouldUpdateNviPeriodSuccessfully()
        throws IOException {
        var persistedPeriod = nviService.createPeriod(randomPeriod());
        var newValue = persistedPeriod.copy().reportingDate(new Date(2050, 0, 25).toInstant()).build();
        handler.handleRequest(createRequest(newValue), output, context);
        var updatedPeriod = nviService.getPeriod(persistedPeriod.publishingYear());

        assertThat(persistedPeriod.reportingDate(), is(not(equalTo(updatedPeriod.reportingDate()))));
    }

    private InputStream createRequest(DbNviPeriod period) throws JsonProcessingException {
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

    private DbNviPeriod randomPeriod() {
        return DbNviPeriod.builder()
                   .reportingDate(new Date(2050, 03, 25).toInstant())
                   .publishingYear(String.valueOf(2023))
                   .createdBy(new DbUsername(randomString()))
                   .build();
    }
}
