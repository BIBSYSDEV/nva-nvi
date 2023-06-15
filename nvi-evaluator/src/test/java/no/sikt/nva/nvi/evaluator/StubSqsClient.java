package no.sikt.nva.nvi.evaluator;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class StubSqsClient implements SqsClient {

    private final List<SendMessageRequest> sentMessages = new ArrayList<>();

    public List<SendMessageRequest> getSentMessages() {
        return sentMessages;
    }

    @Override
    public String serviceName() {
        return null;
    }

    @Override
    public void close() {

    }

    @Override
    public SendMessageResponse sendMessage(SendMessageRequest sendMessageRequest)
        throws AwsServiceException, SdkClientException {
        sentMessages.add(sendMessageRequest);
        return SendMessageResponse.builder()
                   .messageId(sendMessageRequest.messageDeduplicationId())
                   .build();
    }
}
