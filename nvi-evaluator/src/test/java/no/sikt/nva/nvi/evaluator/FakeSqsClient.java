package no.sikt.nva.nvi.evaluator;

import java.util.ArrayList;
import java.util.List;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@JacocoGenerated
public class FakeSqsClient implements SqsClient {

    private final List<SendMessageRequest> sentMessages = new ArrayList<>();

    public List<SendMessageRequest> getSentMessages() {
        return sentMessages;
    }

    @Override
    public SendMessageResponse sendMessage(SendMessageRequest candidate)
        throws AwsServiceException, SdkClientException {
        sentMessages.add(candidate);
        return SendMessageResponse.builder()
                   .messageId(candidate.messageDeduplicationId())
                   .build();
    }

    @Override
    public String serviceName() {
        return null;
    }

    @Override
    public void close() {
        sentMessages.clear();
    }
}
