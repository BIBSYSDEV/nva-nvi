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
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.s3.S3Client;

public class CristinNviReportEventConsumer implements RequestHandler<SQSEvent, Void> {

    public static final String NVI_ERRORS = "NVI_ERRORS";
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

        sqsEvent.getRecords().stream().map(SQSMessage::getBody).forEach(this::processMessageBody);

        return null;
    }

    private static DbCandidate createDbCandidate(CristinNviReport cristinNviReport) {
        return attempt(() -> CristinMapper.toDbCandidate(cristinNviReport)).orElseThrow(
            CristinConversionException::fromFailure);
    }

    private static List<DbApprovalStatus> createApprovals(CristinNviReport cristinNviReport) {
        return attempt(() -> CristinMapper.toApprovals(cristinNviReport)).orElseThrow(
            CristinConversionException::fromFailure);
    }

    /**
     * Method is needed to wrap exception thrown by processBody() to Optional. This allows to persist report for single
     * entry.
     *
     * @param value The string value og event body
     */

    private void processMessageBody(String value) {
        attempt(() -> processBody(value)).toOptional();
    }

    private CandidateDao processBody(String value) {
        var eventReference = EventReference.fromJson(value);
        var cristinNviReport = createNviReport(eventReference);
        try {
            return createAndPersist(cristinNviReport);
        } catch (Exception e) {
            ErrorReport.withMessage(e.getMessage())
                .bucket(eventReference.extractBucketName())
                .key(cristinNviReport.publicationIdentifier())
                .persist(s3Client);
            throw CristinEventConsumerException.withMessage(e.getMessage());
        }
    }

    private CristinNviReport createNviReport(EventReference eventReference) {
        return attempt(() -> fetchS3Content(eventReference)).map(this::toCristinNviReport).orElseThrow();
    }

    private CristinNviReport toCristinNviReport(String value) {
        return attempt(() -> dtoObjectMapper.readValue(value, CristinNviReport.class)).orElseThrow();
    }

    private String fetchS3Content(EventReference eventReference) {
        return new S3Driver(s3Client, eventReference.extractBucketName()).readEvent(eventReference.getUri());
    }

    private CandidateDao createAndPersist(CristinNviReport cristinNviReport) {
        return repository.create(createDbCandidate(cristinNviReport), createApprovals(cristinNviReport),
                                 String.valueOf(cristinNviReport.yearReported()));
    }
}
