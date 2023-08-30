package no.sikt.nva.nvi.index;

import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.INSERT;
import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.MODIFY;
import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.REMOVE;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.index.IndexNviCandidateHandler.DOCUMENT_ADDED_MESSAGE;
import static no.sikt.nva.nvi.index.IndexNviCandidateHandler.DOCUMENT_REMOVED_MESSAGE;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.StringUtils;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IndexNviCandidateHandlerTest extends LocalDynamoTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String CANDIDATE = IoUtils.stringFromResources(Path.of("candidate.json"));
    public static final String CANDIDATE_MISSING_FIELDS = IoUtils.stringFromResources(Path.of("candidateV2.json"));
    public static final String INSTITUTION_ID_FROM_EVENT =
        "https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0";
    private IndexNviCandidateHandler handler;
    private TestAppender appender;
    private StorageReader<URI> storageReader;
    private NviService nviService;

    @BeforeEach
    void setup() {
        storageReader = mock(StorageReader.class);
        SearchClient<NviCandidateIndexDocument> openSearchClient = mock(OpenSearchClient.class);
        nviService = mock(NviService.class);
        handler = new IndexNviCandidateHandler(storageReader, openSearchClient, nviService);
        appender = LogUtils.getTestingAppenderForRootLogger();
        doNothing().when(openSearchClient).addDocumentToIndex(any());
        doNothing().when(openSearchClient).removeDocumentFromIndex(any());
    }

    private static DynamodbStreamRecord candidateRecord(String fileName) throws JsonProcessingException {
        return JsonUtils.dtoObjectMapper.readValue(IoUtils.stringFromResources(Path.of(
            fileName)), DynamodbStreamRecord.class);
    }

    @Test
    void shouldAddDocumentToIndexWhenIncomingEventIsInsert() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE_MISSING_FIELDS);
        when(nviService.findById(any())).thenReturn(Optional.of(randomCandidateWithIdentifier()));

        handler.handleRequest(createEvent(INSERT, candidateRecord("dynamoDbRecordEvent.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(DOCUMENT_ADDED_MESSAGE));
    }

    @Test
    void shouldUpdateExistingIndexDocumentWhenIncomingEventIsModify() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        when(nviService.findById(any())).thenReturn(Optional.of(randomCandidateWithIdentifier()));

        handler.handleRequest(createEvent(MODIFY, candidateRecord("dynamoDbRecordEvent.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(DOCUMENT_ADDED_MESSAGE));
    }

    @Test
    void shouldRemoveDocumentFromIndexWhenIncomingEventIsRemove() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        when(nviService.findById(any())).thenReturn(Optional.of(randomCandidateWithIdentifier()));

        handler.handleRequest(createEvent(REMOVE, candidateRecord("dynamoDbRecordEvent.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(DOCUMENT_REMOVED_MESSAGE));
    }

    @Test
    void shouldNotDoAnythingWhenConsumedRecordIsNotCandidate() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        when(nviService.findById(any())).thenReturn(Optional.of(randomCandidateWithIdentifier()));

        handler.handleRequest(createEvent(REMOVE, candidateRecord("dynamoDbUniqueEntryEvent.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(StringUtils.EMPTY_STRING));
    }

    private static DynamodbEvent createEvent(OperationType operationType, DynamodbStreamRecord record) {
        var event = new DynamodbEvent();
        event.setRecords(List.of(dynamoRecord(operationType, record)));
        return event;
    }

    private static DynamodbEvent.DynamodbStreamRecord dynamoRecord(OperationType operationType,
                                                                   DynamodbStreamRecord record) {

        return (DynamodbStreamRecord) new DynamodbStreamRecord().withEventName(randomElement(operationType))
                                                                .withEventID(randomString())
                                                                .withAwsRegion(randomString())
                                                                .withDynamodb(randomPayload())
                                                                .withEventSource(randomString())
                                                                .withEventVersion(randomString())
                                                                .withDynamodb(record.getDynamodb());
    }

    private static StreamRecord randomPayload() {
        return new StreamRecord().withOldImage(Map.of(randomString(), new AttributeValue(randomString())))
                   .withNewImage(Map.of(randomString(), new AttributeValue(randomString())));
    }

    private static CandidateWithIdentifier randomCandidateWithIdentifier() {
        var candidate = randomCandidateBuilder()
                   .withApprovalStatuses(List.of(getApprovalStatus()))
                   .build();
        return new CandidateWithIdentifier(candidate, randomUUID());
    }

    private static ApprovalStatus getApprovalStatus() {
        return new ApprovalStatus.Builder()
                   .withInstitutionId(URI.create(INSTITUTION_ID_FROM_EVENT))
                   .build();
    }
}
