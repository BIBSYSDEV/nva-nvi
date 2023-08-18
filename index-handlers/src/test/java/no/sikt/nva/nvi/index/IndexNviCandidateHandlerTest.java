package no.sikt.nva.nvi.index;

import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.INSERT;
import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.MODIFY;
import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.REMOVE;
import static no.sikt.nva.nvi.index.IndexNviCandidateHandler.DOCUMENT_ADDED_MESSAGE;
import static no.sikt.nva.nvi.index.IndexNviCandidateHandler.DOCUMENT_REMOVED_MESSAGE;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IndexNviCandidateHandlerTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String CANDIDATE = IoUtils.stringFromResources(Path.of("candidate.json"));
    public static final String CANDIDATE_MISSING_FIELDS = IoUtils.stringFromResources(Path.of("candidateV2.json"));
    private IndexNviCandidateHandler handler;
    private TestAppender appender;
    private StorageReader storageReader;

    @BeforeEach
    void setup() {
        storageReader = mock(StorageReader.class);
        SearchClient<NviCandidateIndexDocument> openSearchClient = mock(OpenSearchClient.class);
        handler = new IndexNviCandidateHandler(storageReader, openSearchClient);
        appender = LogUtils.getTestingAppenderForRootLogger();

        doNothing().when(openSearchClient).addDocumentToIndex(any());
        doNothing().when(openSearchClient).removeDocumentFromIndex(any());
    }

    @Test
    void shouldAddDocumentToIndexWhenIncomingEventIsInsert() {
        when(storageReader.readUri(any())).thenReturn(CANDIDATE_MISSING_FIELDS);

        handler.handleRequest(createEvent(INSERT), CONTEXT);
        assertThat(appender.getMessages(), containsString(DOCUMENT_ADDED_MESSAGE));
    }

    @Test
    void shouldUpdateExistingIndexDocumentWhenIncomingEventIsModify() {
        when(storageReader.readUri(any())).thenReturn(CANDIDATE);

        handler.handleRequest(createEvent(MODIFY), CONTEXT);

        assertThat(appender.getMessages(), containsString(DOCUMENT_ADDED_MESSAGE));
    }

    @Test
    void shouldRemoveDocumentFromIndexWhenIncomingEventIsRemove() {
        when(storageReader.readUri(any())).thenReturn(CANDIDATE);

        handler.handleRequest(createEvent(REMOVE), CONTEXT);

        assertThat(appender.getMessages(), containsString(DOCUMENT_REMOVED_MESSAGE));
    }

    private static DynamodbEvent createEvent(OperationType operationType) {
        var event = new DynamodbEvent();
        event.setRecords(List.of(dynamoRecordWithType(operationType)));
        return event;
    }

    private static DynamodbEvent.DynamodbStreamRecord dynamoRecordWithType(OperationType operationType) {
        return (DynamodbStreamRecord) new DynamodbStreamRecord().withEventName(randomElement(operationType))
                                                                .withEventID(randomString())
                                                                .withAwsRegion(randomString())
                                                                .withDynamodb(randomPayload())
                                                                .withEventSource(randomString())
                                                                .withEventVersion(randomString());
    }

    private static StreamRecord randomPayload() {
        return new StreamRecord().withOldImage(Map.of(randomString(), new AttributeValue(randomString())))
                                 .withNewImage(Map.of(randomString(), new AttributeValue(randomString())));
    }
}
