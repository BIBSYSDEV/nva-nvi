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
    private IndexNviCandidateHandler handler;
    private SearchClient<NviCandidateIndexDocument> openSearchClient;
    private TestAppender appender;

    @BeforeEach
    void setup() {
        var storageReader = mock(StorageReader.class);
        openSearchClient = mock(OpenSearchClient.class);
        handler = new IndexNviCandidateHandler(storageReader, openSearchClient);
        appender = LogUtils.getTestingAppenderForRootLogger();
        doNothing().when(openSearchClient).addDocumentToIndex(any());
        when(storageReader.readUri(any())).thenReturn(CANDIDATE);
    }

    @Test
    void shouldAddDocumentToIndexWhenIncomingEventIsINSERT() {
        handler.handleRequest(createEvent(INSERT), CONTEXT);
        assertThat(appender.getMessages(), containsString(DOCUMENT_ADDED_MESSAGE));
    }

    @Test
    void shouldUpdateExistingIndexDocumentWhenIncomingEventIsMODIFY() {
        doNothing().when(openSearchClient).addDocumentToIndex(any());
        handler.handleRequest(createEvent(MODIFY), CONTEXT);

        assertThat(appender.getMessages(), containsString(DOCUMENT_ADDED_MESSAGE));
    }

    @Test
    void shouldRemoveDocumentFromIndexWhenIncomingEventIsREMOVE() {
        doNothing().when(openSearchClient).addDocumentToIndex(any());
        handler.handleRequest(createEvent(REMOVE), CONTEXT);

        assertThat(appender.getMessages(), containsString(DOCUMENT_REMOVED_MESSAGE));
    }

    private static DynamodbEvent createEvent(OperationType operationType) {
        var event = new DynamodbEvent();
        var records = List.of(dynamoRecordWithType(operationType));
        event.setRecords(records);
        return event;
    }

    private static DynamodbEvent.DynamodbStreamRecord dynamoRecordWithType(OperationType operationType) {
        var record = new DynamodbStreamRecord();
        record.setEventName(randomElement(operationType));
        record.setEventID(randomString());
        record.setAwsRegion(randomString());
        record.setDynamodb(randomPayload());
        record.setEventSource(randomString());
        record.setEventVersion(randomString());
        return record;
    }

    private static StreamRecord randomPayload() {
        var record = new StreamRecord();
        record.setOldImage(randomDynamoPayload());
        record.setNewImage(randomDynamoPayload());
        return record;
    }

    private static Map<String, AttributeValue> randomDynamoPayload() {
        var value = new AttributeValue(CANDIDATE);
        return Map.of(randomString(), value);
    }
}
