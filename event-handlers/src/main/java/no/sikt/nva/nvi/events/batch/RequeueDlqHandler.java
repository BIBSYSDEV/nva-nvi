package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.NviReceiveMessage;
import no.sikt.nva.nvi.common.queue.NviReceiveMessageResponse;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequeueDlqHandler implements RequestHandler<RequeueDlqInput, RequeueDlqOutput> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequeueDlqHandler.class);
    private static final int MAX_FAILURES = 5;
    public static final int MAX_SQS_MESSAGE_COUNT_LIMIT = 10;
    public static final String DUPLICATE_MESSAGE_FOUND_IN_DLQ = "Duplicate message found in DLQ: %s";
    public static final String DLQ_QUEUE_URL_ENV_NAME = "DLQ_QUEUE_URL";
    public static final String HANDLER_FINISH_REPORT_LOG =
        "Requeue DLQ finished. In total {} messages processed. Success count: {}. "
        + "Failure count: {}. {} batches with errors.";
    public static final String REQUEUE_DLQ_STARTED_LOG = "Requeue DLQ started. {} messages to process.";
    public static final String DELETING_MESSAGE_FROM_DLQ_LOG = "Deleting message from DLQ: {}";
    public static final String PROCESSING_MESSAGE_LOG = "Processing message: {}";
    public static final String CANDIDATE_IDENTIFIER_ATTRIBUTE_NAME = "candidateIdentifier";
    public static final String COULD_NOT_UPDATE_CANDIDATE = "Could not update candidate: %s";
    public static final String COULD_NOT_PROCESS_MESSAGE_LOG = "Could not process message: {}";

    private final NviQueueClient queueClient;
    private final String queueUrl;
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;

    @JacocoGenerated
    public RequeueDlqHandler() {
        this(
            new NviQueueClient(),
            new Environment().readEnv(DLQ_QUEUE_URL_ENV_NAME),
            new CandidateRepository(defaultDynamoClient()),
            new PeriodRepository(defaultDynamoClient()));
    }

    public RequeueDlqHandler(NviQueueClient queueClient, String dlqQueueUrl, CandidateRepository candidateRepository,
                             PeriodRepository periodRepository) {
        this.queueClient = queueClient;
        this.queueUrl = dlqQueueUrl;
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
    }

    @Override
    public RequeueDlqOutput handleRequest(RequeueDlqInput input, Context context) {
        LOGGER.info(REQUEUE_DLQ_STARTED_LOG, input.count());

        Set<String> messageIdDuplicateCheck = new HashSet<>();

        var remainingMessages = input.count();
        var failedBatchesCount = 0;
        var result = new HashSet<NviProcessMessageResult>();

        while (remainingMessages > 0) {
            var messagesToReceive = Math.min(remainingMessages, MAX_SQS_MESSAGE_COUNT_LIMIT);
            var response = queueClient.receiveMessage(queueUrl, messagesToReceive);

            if (shouldBreakLoop(response, failedBatchesCount)) {
                break;
            }

            var processedMessages = processMessages(response, messageIdDuplicateCheck);

            result.addAll(processedMessages);
            failedBatchesCount += checkForFailedBatch(processedMessages);
            remainingMessages -= messagesToReceive;
        }

        LOGGER.info(
            HANDLER_FINISH_REPORT_LOG,
            result.size(),
            result.stream().filter(NviProcessMessageResult::success).count(),
            result.stream().filter(a -> !a.success()).count(),
            failedBatchesCount);

        return new RequeueDlqOutput(result, failedBatchesCount);
    }

    private boolean shouldBreakLoop(NviReceiveMessageResponse response, int failedBatchesCount) {
        return response.messages().isEmpty() || failedBatchesCount >= MAX_FAILURES;
    }

    private Set<NviProcessMessageResult> processMessages(NviReceiveMessageResponse response,
                                                         Set<String> messageIdDuplicateCheck) {
        return response.messages().stream()
                   .map(message -> checkForDuplicates(messageIdDuplicateCheck, message))
                   .map(this::processMessage)
                   .map(this::deleteMessageFromDlq)
                   .collect(Collectors.toSet());
    }

    private static NviProcessMessageResult checkForDuplicates(Set<String> messageIdDuplicateCheck,
                                                              NviReceiveMessage message) {
        var isUnique = messageIdDuplicateCheck.add(message.messageId());
        if (!isUnique) {
            var warning = String.format(DUPLICATE_MESSAGE_FOUND_IN_DLQ, message.messageId());
            LOGGER.warn(warning);
            return new NviProcessMessageResult(
                message,
                false,
                Optional.of(warning));
        }
        return new NviProcessMessageResult(message, true, Optional.empty());
    }

    private int checkForFailedBatch(Set<NviProcessMessageResult> processedMessages) {
        return processedMessages.stream().anyMatch(a -> !a.success()) ? 1 : 0;
    }

    private NviProcessMessageResult deleteMessageFromDlq(NviProcessMessageResult message) {
        LOGGER.info(DELETING_MESSAGE_FROM_DLQ_LOG, message.message().body());
        if (message.success()) {
            queueClient.deleteMessage(queueUrl, message.message().receiptHandle());
        }
        return message;
    }

    private NviProcessMessageResult processMessage(NviProcessMessageResult input) {
        LOGGER.info(PROCESSING_MESSAGE_LOG, input.message().body());

        if (!input.success()) {
            return input;
        }

        try {
            var identifier = input.message().messageAttributes().get(CANDIDATE_IDENTIFIER_ATTRIBUTE_NAME);
            var candidate = Candidate.fromRequest(() -> UUID.fromString(identifier), candidateRepository,
                                                  periodRepository);
            var nviCandidate = NviCandidate.fromCandidate(candidate);

            var updatedCandidate = Candidate.fromRequest(nviCandidate, candidateRepository, periodRepository);

            if (updatedCandidate.isEmpty()) {
                return new NviProcessMessageResult(input.message(), false,
                                                   Optional.of(String.format(COULD_NOT_UPDATE_CANDIDATE, identifier)));
            }
        } catch (Exception e) {
            LOGGER.error(COULD_NOT_PROCESS_MESSAGE_LOG, input.message().body(), e);
            return new NviProcessMessageResult(input.message(), false, Optional.of(getStackTrace(e)));
        }

        return new NviProcessMessageResult(input.message(), true, Optional.empty());
    }

    private static String getStackTrace(Exception exception) {
        var stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }
}
