package no.sikt.nva.nvi.events.batch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public final class RequeueDlqTestUtils {

  private RequeueDlqTestUtils() {
    // NO-OP
  }

  public static SqsClient setupSqsClient() {
    var client = mock(SqsClient.class);

    var status = SdkHttpResponse.builder().statusCode(202).build();
    var response = DeleteMessageResponse.builder();
    response.sdkHttpResponse(status);

    when(client.deleteMessage(any(DeleteMessageRequest.class))).thenReturn(response.build());

    return client;
  }

  public static List<Message> generateMessages(int count, String batchPrefix) {
    return generateMessages(count, batchPrefix, UUID.randomUUID());
  }

  public static List<Message> generateMessages(
      int count, String batchPrefix, UUID candidateIdentifier) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(
            i ->
                Message.builder()
                    .messageId(batchPrefix + i)
                    .receiptHandle(batchPrefix + i)
                    .messageAttributes(
                        Map.of(
                            "candidateIdentifier",
                            MessageAttributeValue.builder()
                                .stringValue(candidateIdentifier.toString())
                                .build()))
                    .build())
        .collect(Collectors.toList());
  }
}
