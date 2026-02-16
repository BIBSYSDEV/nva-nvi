package no.sikt.nva.nvi.common.queue;

import java.util.Collection;
import java.util.UUID;

public interface QueueClient {

  NviSendMessageResponse sendMessage(String message, String queueUrl);

  NviSendMessageResponse sendMessage(QueueMessage message, String queueUrl);

  NviSendMessageResponse sendMessage(String message, String queueUrl, UUID candidateIdentifier);

  NviSendMessageBatchResponse sendMessageBatch(Collection<String> messages, String queueUrl);

  NviReceiveMessageResponse receiveMessage(String queueUrl, int maxNumberOfMessages);

  void deleteMessage(String dlqQueueUrl, String receiptHandle);
}
