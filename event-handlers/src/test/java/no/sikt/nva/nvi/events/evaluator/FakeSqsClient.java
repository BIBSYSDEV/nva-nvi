package no.sikt.nva.nvi.events.evaluator;

import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.QueueClient;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@JacocoGenerated
public class FakeSqsClient implements QueueClient<NviSendMessageResponse> {

    private final List<SendMessageRequest> sentMessages = new ArrayList<>();

    public List<SendMessageRequest> getSentMessages() {
        return sentMessages;
    }

    @Override
    public NviSendMessageResponse sendMessage(String message, String queueUrl) {
        var request = createRequest(message, queueUrl);
        sentMessages.add(request);
        return createResponse(SendMessageResponse.builder()
                                  .messageId(request.messageDeduplicationId())
                                  .build());
    }

    private SendMessageRequest createRequest(String body, String queueUrl) {
        return SendMessageRequest.builder()
                   .queueUrl(queueUrl)
                   .messageBody(body)
                   .build();
    }

    private NviSendMessageResponse createResponse(SendMessageResponse response) {
        return new NviSendMessageResponse(response.messageId());
    }
}
