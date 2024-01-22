package no.sikt.nva.nvi.events.cristin;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

public class CristinNviReportEventConsumer implements RequestHandler<SQSEvent, Void> {

    private static final Logger logger = LoggerFactory.getLogger(CristinNviReportEventConsumer.class);
    private final CandidateRepository repository;
    private final S3Client s3Client;

    @JacocoGenerated
    public CristinNviReportEventConsumer() {
        this.repository = new CandidateRepository(defaultDynamoClient());
        this.s3Client = S3Driver.defaultS3Client().build();
    }

    public CristinNviReportEventConsumer(CandidateRepository candidateRepository, S3Client s3Client) {
        this.repository = candidateRepository;
        this.s3Client = s3Client;
    }

    @Override
    public Void handleRequest(SQSEvent sqsEvent, Context context) {

        sqsEvent.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(EventReference::fromJson)
            .map(this::fetchS3Content)
            .map(this::toEventBody)
            .map(EventReferenceWithContent::cristinNviReport)
            .forEach(this::createAndPersist);

        return null;
    }

    private EventReferenceWithContent toEventBody(String value) {
        logger.info("Fetched string from s3: {}", value);
        return attempt(() -> dtoObjectMapper.readValue(value, EventReferenceWithContent.class)).orElseThrow();
    }

    private String fetchS3Content(EventReference eventReference) {
        logger.info("Event to fetch: {}", eventReference.toJsonString());
        return new S3Driver(s3Client, eventReference.extractBucketName())
                   .readEvent(eventReference.getUri());
    }

    private void createAndPersist(CristinNviReport cristinNviReport) {
        logger.info("Nvi report: {}", cristinNviReport.toJsonString());
        repository.create(CristinMapper.toDbCandidate(cristinNviReport), CristinMapper.toApprovals(cristinNviReport));
    }
}
