package no.sikt.nva.nvi.common.queue;

import java.util.Collection;

public interface QueueClient {

  NviSendMessageResponse sendMessage(String message, String queueUrl);

  NviSendMessageResponse sendMessage(QueueMessage message, String queueUrl);

  NviSendMessageBatchResponse sendMessageBatch(Collection<String> messages, String queueUrl);

  NviReceiveMessageResponse receiveMessage(String queueUrl, int maxNumberOfMessages);

  void deleteMessage(String dlqQueueUrl, String receiptHandle);
}
