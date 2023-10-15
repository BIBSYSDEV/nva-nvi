package no.sikt.nva.nvi.evaluator.aws;

import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.sqs.SqsClient;

public class EvaluateNviCandidateSqsClient extends NviQueueClient {
    private final String queueUrl;
    private final String dlqUrl;

    @JacocoGenerated
    public EvaluateNviCandidateSqsClient() {
        this(defaultSqsClient(), new Environment());
    }

    public EvaluateNviCandidateSqsClient(SqsClient sqsClient, Environment env) {
        super(sqsClient);
        this.queueUrl = env.readEnv("CANDIDATE_QUEUE_URL");
        this.dlqUrl = env.readEnv("CANDIDATE_DLQ_URL");
    }

    public NviSendMessageResponse sendDlq(String message) {
        return sendMessage(message, dlqUrl);
    }

    public NviSendMessageResponse sendMessage(String message) {
        return sendMessage(message, queueUrl);
    }
}