package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.NviReceiveMessage;
import no.sikt.nva.nvi.common.queue.NviReceiveMessageResponse;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.events.persist.UpsertDlqMessageBody;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RedriveUpsertDlqHandlerTest {

  private static final Environment ENVIRONMENT =
      new FakeEnvironment(
          Map.of(
              "UPSERT_CANDIDATE_DLQ_QUEUE_URL", randomString(),
              "PERSISTED_RESOURCE_QUEUE_URL", randomString(),
              "EXPANDED_RESOURCES_BUCKET", randomString()));
  private static final String DLQ_URL = ENVIRONMENT.readEnv("UPSERT_CANDIDATE_DLQ_QUEUE_URL");
  private static final String EVALUATION_QUEUE_URL =
      ENVIRONMENT.readEnv("PERSISTED_RESOURCE_QUEUE_URL");
  private static final String BUCKET_NAME = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
  private static final Context CONTEXT = mock(Context.class);

  private NviQueueClient queueClient;
  private RedriveUpsertDlqHandler handler;

  @BeforeEach
  void setUp() {
    queueClient = mock(NviQueueClient.class);
    handler = new RedriveUpsertDlqHandler(queueClient, ENVIRONMENT);
  }

  @Test
  void shouldProcessMessagesWithPublicationBucketUri() throws JsonProcessingException {
    var publicationId = randomUri();
    var publicationBucketUri = createS3Uri(publicationId);
    var candidateMessage = createCandidateEvaluatedMessage(publicationId, publicationBucketUri);
    var dlqMessageBody = createDlqMessageBody(candidateMessage);

    var receiveMessage =
        new NviReceiveMessage(dlqMessageBody, randomString(), Map.of(), randomString());
    var receiveResponse = new NviReceiveMessageResponse(List.of(receiveMessage));
    var emptyResponse = new NviReceiveMessageResponse(List.of());

    when(queueClient.receiveMessage(eq(DLQ_URL), anyInt()))
        .thenReturn(receiveResponse)
        .thenReturn(emptyResponse);
    when(queueClient.sendMessage(eq(EVALUATION_QUEUE_URL), any()))
        .thenReturn(new NviSendMessageResponse(randomString()));

    var input = new RedriveUpsertDlqInput(1);
    handler.handleRequest(input, CONTEXT);

    var argumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(queueClient).sendMessage(eq(EVALUATION_QUEUE_URL), argumentCaptor.capture());

    var sentMessage =
        dtoObjectMapper.readValue(argumentCaptor.getValue(), PersistedResourceMessage.class);
    assertThat(sentMessage.resourceFileUri(), is(equalTo(publicationBucketUri)));

    verify(queueClient).deleteMessage(eq(DLQ_URL), eq(receiveMessage.receiptHandle()));
  }

  private static String createDlqMessageBody(CandidateEvaluatedMessage candidateMessage) {
    return UpsertDlqMessageBody.create(candidateMessage, new Exception()).toJsonString();
  }

  @Test
  void shouldThrowExceptionWhenFailingProcessingDlqMessagesAndKeepMessagesOnDlq() {
    var malformedMessage =
        new NviReceiveMessage("invalid json", randomString(), Map.of(), randomString());
    var receiveResponse = new NviReceiveMessageResponse(List.of(malformedMessage));
    var emptyResponse = new NviReceiveMessageResponse(List.of());

    when(queueClient.receiveMessage(eq(DLQ_URL), anyInt()))
        .thenReturn(receiveResponse)
        .thenReturn(emptyResponse);

    LogUtils.getTestingAppenderForRootLogger();
    var input = new RedriveUpsertDlqInput(1);
    assertThrows(RuntimeException.class, () -> handler.handleRequest(input, CONTEXT));

    verify(queueClient, times(0)).deleteMessage(any(), any());
  }

  @Test
  void shouldHandleMultipleBatches() {
    var messages = createMultipleMessages(15);
    var firstBatch = new NviReceiveMessageResponse(messages.subList(0, 10));
    var secondBatch = new NviReceiveMessageResponse(messages.subList(10, 15));
    var emptyResponse = new NviReceiveMessageResponse(List.of());

    when(queueClient.receiveMessage(eq(DLQ_URL), anyInt()))
        .thenReturn(firstBatch)
        .thenReturn(secondBatch)
        .thenReturn(emptyResponse);
    when(queueClient.sendMessage(eq(EVALUATION_QUEUE_URL), any()))
        .thenReturn(new NviSendMessageResponse(randomString()));

    var input = new RedriveUpsertDlqInput(15);
    handler.handleRequest(input, CONTEXT);

    verify(queueClient, times(15)).deleteMessage(eq(DLQ_URL), any());
  }

  @Test
  void shouldHandleDuplicateMessages() {
    var publicationId = randomUri();
    var candidateMessage =
        createCandidateEvaluatedMessage(publicationId, createS3Uri(publicationId));
    var dlqMessageBody = createDlqMessageBody(candidateMessage);
    var messageId = randomString();

    var message1 = new NviReceiveMessage(dlqMessageBody, messageId, Map.of(), randomString());
    var message2 = new NviReceiveMessage(dlqMessageBody, messageId, Map.of(), randomString());

    var receiveResponse = new NviReceiveMessageResponse(List.of(message1, message2));
    var emptyResponse = new NviReceiveMessageResponse(List.of());

    when(queueClient.receiveMessage(eq(DLQ_URL), anyInt()))
        .thenReturn(receiveResponse)
        .thenReturn(emptyResponse);
    when(queueClient.sendMessage(eq(EVALUATION_QUEUE_URL), any()))
        .thenReturn(new NviSendMessageResponse(randomString()));

    var input = new RedriveUpsertDlqInput(2);
    handler.handleRequest(input, CONTEXT);

    verify(queueClient, times(2)).deleteMessage(eq(DLQ_URL), any());
    verify(queueClient, times(1)).sendMessage(eq(EVALUATION_QUEUE_URL), any());
  }

  @Test
  void shouldUseDefaultMessageCountWhenInputIsNull() {
    var emptyResponse = new NviReceiveMessageResponse(List.of());
    when(queueClient.receiveMessage(eq(DLQ_URL), eq(10))).thenReturn(emptyResponse);

    handler.handleRequest(null, CONTEXT);

    verify(queueClient).receiveMessage(eq(DLQ_URL), eq(10));
  }

  private CandidateEvaluatedMessage createCandidateEvaluatedMessage(
      URI publicationId, URI s3BucketUri) {
    return CandidateEvaluatedMessage.builder()
        .withCandidateType(
            randomUpsertRequestBuilder()
                .withPublicationId(publicationId)
                .withPublicationBucketUri(s3BucketUri)
                .build())
        .build();
  }

  private List<NviReceiveMessage> createMultipleMessages(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(
            i -> {
              var publicationId = randomUri();
              var candidateMessage =
                  createCandidateEvaluatedMessage(publicationId, createS3Uri(publicationId));
              var dlqMessageBody = createDlqMessageBody(candidateMessage);
              return new NviReceiveMessage(
                  dlqMessageBody, randomString(), Map.of(), randomString());
            })
        .toList();
  }

  private URI createS3Uri(URI publicationId) {
    var identifier = UriWrapper.fromUri(publicationId).getLastPathElement();
    return UriWrapper.fromUri("s3://" + BUCKET_NAME)
        .addChild("resources")
        .addChild(identifier + ".gz")
        .getUri();
  }
}
