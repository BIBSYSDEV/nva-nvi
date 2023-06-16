package no.sikt.nva.nvi.evaluator;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

class StubQueueClient implements QueueClient<SendMessageResponse> {

    private final List<SendMessageRequest> sentMessages = new ArrayList<>();

    public List<SendMessageRequest> getSentMessages() {
        return sentMessages;
    }


    @Override
    public SendMessageResponse sendMessage(String body)
        throws AwsServiceException, SdkClientException {
        SendMessageRequest candidate = createCandidate(body);
        sentMessages.add(candidate);
        return SendMessageResponse.builder()
                   .messageId(candidate.messageDeduplicationId())
                   .build();
    }
    private SendMessageRequest createCandidate(String body) {
        return SendMessageRequest.builder().messageBody(body).build();
    }
}
