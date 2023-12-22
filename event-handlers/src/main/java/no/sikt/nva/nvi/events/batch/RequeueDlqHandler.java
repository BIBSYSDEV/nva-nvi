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

    private final NviQueueClient queueClient;
    private final String queueUrl;
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;

    @JacocoGenerated
    public RequeueDlqHandler() {
        this(
            new NviQueueClient(),
            new Environment().readEnv("DLQ_QUEUE_URL"),
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
        LOGGER.info("Requeue DLQ started. {} messages to process.", input.count());

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
            "Requeue DLQ finished. In total {} messages processed. Success count: {}. "
            + "Failure count: {}. {} batches with errors.",
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
        LOGGER.info("Deleting message from DLQ: {}", message.message().body());
        if (message.success()) {
            queueClient.deleteMessage(queueUrl, message.message().receiptHandle());
        }
        return message;
    }

    private NviProcessMessageResult processMessage(NviProcessMessageResult input) {
        LOGGER.info("Processing message: {}", input.message().body());

        if (!input.success()) {
            return input;
        }

        try {
            var identifier = input.message().messageAttributes().get("candidateIdentifier");
            var candidate = Candidate.fromRequest(() -> UUID.fromString(identifier), candidateRepository,
                                                  periodRepository);
            var nviCandidate = NviCandidate.fromCandidate(candidate);

            var updatedCandidate = Candidate.fromRequest(nviCandidate, candidateRepository, periodRepository);

            if (updatedCandidate.isEmpty()) {
                return new NviProcessMessageResult(input.message(), false,
                                                   Optional.of("Could not update candidate: " + identifier));
            }
        } catch (Exception e) {
            LOGGER.error("Could not process message: " + input.message().body(), e);
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
