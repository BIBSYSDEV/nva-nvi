package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.events.batch.RequeueDlqTestUtils.generateMessages;
import static no.sikt.nva.nvi.events.batch.RequeueDlqTestUtils.setUpSqsClient;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

public class RequeueDlqHandlerTest {

    public static final Context CONTEXT = mock(Context.class);
    private RequeueDlqHandler handler;
    private SqsClient sqsClient;
    private static final String DLQ_URL = "https://some-sqs-url";
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    void setUp() {
        sqsClient = setUpSqsClient();

        var client = new NviQueueClient(sqsClient);

        candidateRepository = setupCandidateRepository();

        periodRepository = mock(PeriodRepository.class);

        handler = new RequeueDlqHandler(client, DLQ_URL, candidateRepository, periodRepository);
    }

    private static CandidateRepository setupCandidateRepository() {
        var repo = mock(CandidateRepository.class);

        var candidate = createCandidateDao();

        when(repo.findByPublicationId(any()))
            .thenReturn(Optional.of(candidate));

        when(repo.findCandidateById(any()))
            .thenReturn(Optional.of(candidate));

        return repo;
    }

    @Test
    void shouldRequeueSingleBatch() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(generateMessages(1, "firstBatch")).build())
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        var response = handler.handleRequest(new RequeueDlqInput(1), CONTEXT);

        assertEquals(1, getSuccessCount(response));
        assertEquals(0, response.failedBatchesCount());
    }

    @Test
    void shouldWriteToDynamodbWhenProcessing() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(generateMessages(1, "firstBatch")).build())
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        handler.handleRequest(new RequeueDlqInput(1), CONTEXT);

        verify(candidateRepository, times(1)).updateCandidate(any());
    }

    @Test
    void shouldRequeueMultipleBatches() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenAnswer(new Answer<ReceiveMessageResponse>() {
                private int count = 0;

                public ReceiveMessageResponse answer(InvocationOnMock invocation) {
                    var arg = invocation.getArgument(0, ReceiveMessageRequest.class);
                    return ReceiveMessageResponse.builder()
                               .messages(generateMessages(arg.maxNumberOfMessages(), count++ + " batch"))
                               .build();
                }
            });

        var response = handler.handleRequest(new RequeueDlqInput(23), CONTEXT);

        assertEquals(23, getSuccessCount(response));
        assertEquals(0, response.failedBatchesCount());
    }

    @Test
    void shouldHandleEmptyResult() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);

        assertEquals(0, getSuccessCount(response));
        assertEquals(0, response.failedBatchesCount());
    }

    @Test
    void emptyInputShouldDefaultTo10() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(generateMessages(10, "firstBatch")).build())
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        var response = handler.handleRequest(new RequeueDlqInput(), CONTEXT);

        assertEquals(10, getSuccessCount(response));
        assertEquals(0, response.failedBatchesCount());
    }

    @Test
    void shouldIgnoreDuplicates() {
        var message = Message.builder()
                          .messageId("sameOldThing")
                          .receiptHandle("sameOldThing")
                          .messageAttributes(Map.of("candidateIdentifier", MessageAttributeValue.builder().stringValue(
                              UUID.randomUUID().toString()).build()))
                          .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of(message)).build())
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of(message)).build());

        var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);

        assertEquals(1, getSuccessCount(response));
        assertEquals(5, response.failedBatchesCount());
    }

    private static long getSuccessCount(RequeueDlqOutput response) {
        return response.messages().stream().filter(NviProcessMessageResult::success).count();
    }

    @Test
    void missingCustomAttributeShouldFail() {
        var message = Message.builder()
                          .messageId("simpleMessage")
                          .receiptHandle("simpleMessage")
                          .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of(message)).build())
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);

        assertEquals(1, getFailureCount(response));

        var error = response.messages().stream().filter(a -> !a.success()).findFirst().get().error().get();
        assertTrue(error.contains("Exception"));
    }

    private static long getFailureCount(RequeueDlqOutput response) {
        return response.messages().stream().filter(a -> !a.success()).count();
    }

    @Test
    void shouldStopRepeatedErrors() {
        var message = Message.builder()
                          .messageId("sameOldThing")
                          .receiptHandle("sameOldThing")
                          .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of(message)).build());

        var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);

        assertEquals(0, getSuccessCount(response));
        assertEquals(5, response.failedBatchesCount());
    }

    @Test
    void outputShouldPassMessage() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder()
                            .messages(generateMessages(1, "myInput")).build());

        var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);


        var message = response.messages().stream().filter(NviProcessMessageResult::success).findFirst()
                                .get().message().messageId();

        assertTrue(message.contains("myInput"));
    }

    @Test
    void shouldHandleFailureToWriteUpdatedCandidate() {
        var candidate = createCandidateDao();

        when(candidateRepository.findByPublicationId(any()))
            .thenReturn(Optional.of(candidate))
            .thenReturn(Optional.empty());

        when(candidateRepository.findCandidateById(any()))
            .thenReturn(Optional.of(candidate));

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
            .thenReturn(ReceiveMessageResponse.builder()
                            .messages(generateMessages(1, "somPrefix")).build())
            .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);
        assertEquals(1, response.failedBatchesCount());
    }

    @Test
    void testMissingInputCount() throws JsonProcessingException {
        var input = JsonUtils.dtoObjectMapper.readValue("{}", RequeueDlqInput.class);
        assertEquals(10, input.count());
    }

    private static CandidateDao createCandidateDao() {
        return CandidateDao.builder()
                   .candidate(DbCandidate.builder()
                                  .publicationDate(
                                      DbPublicationDate.builder()
                                          .day("1")
                                          .month("1")
                                          .year("2000").build())
                                  .points(List.of(
                                      DbInstitutionPoints.builder()
                                          .institutionId(randomUri())
                                          .points(BigDecimal.valueOf(1))
                                          .build()))
                                  .instanceType(InstanceType.ACADEMIC_ARTICLE.getValue())
                                  .creators(List.of(new DbCreator(randomUri(), List.of(randomUri()))))
                                  .level(DbLevel.LEVEL_ONE)
                                  .channelType(ChannelType.JOURNAL)
                                  .totalPoints(BigDecimal.valueOf(1))
                                  .publicationId(randomUri())
                                  .applicable(true)
                                  .publicationBucketUri(randomUri())
                                  .build())
                   .build();
    }
}
