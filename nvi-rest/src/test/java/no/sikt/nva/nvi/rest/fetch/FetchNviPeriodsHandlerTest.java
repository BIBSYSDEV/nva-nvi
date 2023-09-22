package no.sikt.nva.nvi.rest.fetch;

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
import java.util.Date;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.model.NviPeriodDto;
import no.sikt.nva.nvi.rest.model.NviPeriodsResponse;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FetchNviPeriodsHandlerTest extends LocalDynamoTest {

    private Context context;
    private ByteArrayOutputStream output;
    private FetchNviPeriodsHandler handler;
    private NviService nviService;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        context = new FakeContext();
        nviService = new NviService(initializeTestDatabase());
        handler = new FetchNviPeriodsHandler(nviService);
    }

    @Test
    void shouldThrowUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(createRequestWithAccessRight(AccessRight.USER), output, context);
        var response = GatewayResponse.fromOutputStream(output, NviPeriodsResponse.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnPeriodsWhenUserHasAccessRights() throws IOException {
        nviService.createPeriod(periodWithPublishingYear("2100"));
        nviService.createPeriod(periodWithPublishingYear("2101"));
        handler.handleRequest(createRequestWithAccessRight(AccessRight.MANAGE_NVI_PERIODS), output, context);
        var response = GatewayResponse.fromOutputStream(output, NviPeriodsResponse.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
        assertThat(response.getBodyObject(NviPeriodsResponse.class).periods(), hasSize(2));
    }

    @Test
    void shouldReturnSuccessWhenThereIsNoPeriods() throws IOException {
        handler.handleRequest(createRequestWithAccessRight(AccessRight.MANAGE_NVI_PERIODS), output, context);
        var response = GatewayResponse.fromOutputStream(output, NviPeriodsResponse.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
        assertThat(response.getBodyObject(NviPeriodsResponse.class).periods(), hasSize(0));
    }

    private InputStream createRequestWithAccessRight(AccessRight accessRight) throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<NviPeriodDto>(JsonUtils.dtoObjectMapper)
                   .withCurrentCustomer(customerId)
                   .withAccessRights(customerId, accessRight.name())
                   .withUserName(randomString())
                   .build();
    }

    private static DbNviPeriod periodWithPublishingYear(String publishingYear) {
        return DbNviPeriod.builder()
                   .publishingYear(publishingYear)
                   .reportingDate(new Date(2050, 0, 25).toInstant())
                   .createdBy(DbUsername.fromString(randomString()))
                   .build();
    }
}
