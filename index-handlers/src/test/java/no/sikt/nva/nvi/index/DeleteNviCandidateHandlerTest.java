package no.sikt.nva.nvi.index;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.List;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.DeleteNviCandidateMessageBody;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DeleteNviCandidateHandlerTest {

    public static final String REMOVED_MESSAGE = "Document with id has been removed from index:";

    @Test
    void shouldDeleteDocumentFromIndex() throws JsonProcessingException {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var handler = new DeleteNviCandidateHandler(new FakeSearchClient());
        var document = randomDocument();
        handler.handleRequest(createEvent(document), mock(Context.class));
        assertThat(appender.getMessages(), containsString(REMOVED_MESSAGE));
    }

    @Test
    void shouldThrowExceptionAndLogWhenDocumentDeletionFails() throws IOException {
        var openSearchClient = mock(OpenSearchClient.class);
        var document = randomDocument();
        Mockito.doThrow(new IOException()).when(openSearchClient).removeDocumentFromIndex(document);
        var handler = new DeleteNviCandidateHandler(openSearchClient);
        assertThrows(RuntimeException.class, () -> handler.handleRequest(createEvent(document), mock(Context.class)));
    }

    private static SQSEvent createEvent(NviCandidateIndexDocument document) throws JsonProcessingException {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        var body = constructBody(document);
        message.setBody(body);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    private static String constructBody(NviCandidateIndexDocument document) throws JsonProcessingException {
        return JsonUtils.dtoObjectMapper.writeValueAsString(new DeleteNviCandidateMessageBody(document.identifier()));
    }

    private NviCandidateIndexDocument randomDocument() {
        return new NviCandidateIndexDocument.Builder()
                   .withIdentifier(randomString())
                   .build();
    }
}
