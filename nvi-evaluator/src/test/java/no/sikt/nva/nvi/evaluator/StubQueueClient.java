package no.sikt.nva.nvi.evaluator;

import no.sikt.nva.nvi.evaluator.aws.SqsMessageClient;
import software.amazon.awssdk.services.sqs.SqsClient;

class StubQueueClient extends SqsMessageClient {

    public StubQueueClient(SqsClient sqsClient) {
        super(sqsClient);
    }
}
