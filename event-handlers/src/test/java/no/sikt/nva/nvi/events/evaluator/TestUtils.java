package no.sikt.nva.nvi.events.evaluator;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.model.Organization;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.events.models.AwsEventBridgeDetail;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.events.models.EventReference;

public final class TestUtils {

    private TestUtils() {

    }

    public static InputStream createS3Event(URI uri) throws IOException {
        return createEventInputStream(new EventReference("", uri));
    }

    public static SQSEvent createEvent(PersistedResourceMessage persistedResourceMessage) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        var body = attempt(() -> objectMapper.writeValueAsString(persistedResourceMessage)).orElseThrow();
        message.setBody(body);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    @SuppressWarnings("unchecked")
    public static HttpResponse<String> createResponse(int status, String body) {
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    public static void mockOrganizationResponseForAffiliation(URI topLevelInstitutionId, URI subUnitId,
                                                              UriRetriever uriRetriever) {
        var body = generateResponseBody(topLevelInstitutionId, subUnitId);
        var response = Optional.of(createResponse(200, body));
        if (isNull(subUnitId)) {
            when(uriRetriever.fetchResponse(eq(topLevelInstitutionId), any())).thenReturn(response);
        } else {
            when(uriRetriever.fetchResponse(eq(subUnitId), any())).thenReturn(response);
        }
    }

    private static String generateResponseBody(URI topLevelInstitutionId, URI subUnitId) {
        return attempt(
            () -> dtoObjectMapper.writeValueAsString(isNull(subUnitId)
                                                         ? new Organization(topLevelInstitutionId, emptyList(), null,
                                                                            null, null)
                                                         : new Organization(subUnitId, List.of(
                                                             new Organization(topLevelInstitutionId,
                                                                              emptyList(), null, null, null)), null,
                                                                            null, null))).orElseThrow();
    }

    private static InputStream createEventInputStream(EventReference eventReference) throws IOException {
        var detail = new AwsEventBridgeDetail<EventReference>();
        detail.setResponsePayload(eventReference);
        var event = new AwsEventBridgeEvent<AwsEventBridgeDetail<EventReference>>();
        event.setDetail(detail);
        return new ByteArrayInputStream(dtoObjectMapper.writeValueAsBytes(event));
    }
}
