package no.sikt.nva.nvi.events.cristin;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.List;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.s3.S3Client;

public class CristinNviReportEventConsumer implements RequestHandler<SQSEvent, Void> {

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
            .map(this::toCristinNviReport)
            .forEach(this::createAndPersist);

        return null;
    }

    private CristinNviReport toCristinNviReport(String value) {
        return attempt(() -> dtoObjectMapper.readValue(value, CristinNviReport.class)).orElseThrow();
    }

    private String fetchS3Content(EventReference eventReference) {
        return new S3Driver(s3Client, eventReference.extractBucketName())
                   .readEvent(eventReference.getUri());
    }

    private void createAndPersist(CristinNviReport cristinNviReport) {
        repository.create(createDbCandidate(cristinNviReport),
                          createApprovals(cristinNviReport),
                          String.valueOf(cristinNviReport.yearReported()));
    }

    private static DbCandidate createDbCandidate(CristinNviReport cristinNviReport) {
        return attempt(() -> CristinMapper.toDbCandidate(cristinNviReport))
                   .orElseThrow(CristinConversionException::fromFailure);
    }

    private static List<DbApprovalStatus> createApprovals(CristinNviReport cristinNviReport) {
        return attempt(() -> CristinMapper.toApprovals(cristinNviReport))
                   .orElseThrow(CristinConversionException::fromFailure);
    }
}
