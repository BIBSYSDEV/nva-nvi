package no.sikt.nva.nvi.events.cristin;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.events.db.DynamoDbEventToQueueHandler;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CristinNviReportEventConsumer implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CristinNviReportEventConsumer.class);
    public static final String PARSE_EVENT_BODY_ERROR_MESSAGE = "Could not parse event body: ";
    private final CandidateRepository repository;

    @JacocoGenerated
    public CristinNviReportEventConsumer() {
        this.repository = new CandidateRepository(defaultDynamoClient());
    }

    public CristinNviReportEventConsumer(CandidateRepository candidateRepository) {
        this.repository = candidateRepository;
    }

    @Override
    public Void handleRequest(SQSEvent sqsEvent, Context context) {

        sqsEvent.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(this::toCristinNviReport)
            .forEach(this::createAndPersist);

        return null;
    }


    private void createAndPersist(CristinNviReport cristinNviReport) {
        repository.create(CristinMapper.toDbCandidate(cristinNviReport), CristinMapper.toApprovals(cristinNviReport));
    }

    private CristinNviReport toCristinNviReport(String body) {
        LOGGER.info("Creating nvi candidate from event body {}", body);
        return attempt(() -> dtoObjectMapper.readValue(body, CristinNviReport.class))
                   .orElseThrow(new RuntimeException(PARSE_EVENT_BODY_ERROR_MESSAGE + body));
    }
}
