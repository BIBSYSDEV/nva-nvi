package no.sikt.nva.nvi.events.cristin;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.API_HOST;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.services.s3.S3Client;

public class CristinNviReportEventConsumer implements RequestHandler<SQSEvent, Void> {

  public static final String NVI_ERRORS = "NVI_ERRORS";
  public static final String MISSING_REPORTED_YEAR_MESSAGE = "Reported year is missing!";
  public static final String CRISTIN_DEPARTMENT_TRANSFERS = "cristin_transfer_departments.csv";
  public static final String CRISTIN_DEPARTMENT_TRANSFERS_STRING =
      IoUtils.stringFromResources(Path.of(CRISTIN_DEPARTMENT_TRANSFERS));
  protected static final String PUBLICATION = "publication";
  private final CandidateRepository repository;
  private final S3Client s3Client;
  private final CristinMapper cristinMapper;

  @JacocoGenerated
  public CristinNviReportEventConsumer() {
    this.repository = new CandidateRepository(defaultDynamoClient());
    this.s3Client = S3Driver.defaultS3Client().build();
    this.cristinMapper = CristinMapper.withDepartmentTransfers(readCristinDepartments());
  }

  public CristinNviReportEventConsumer(CandidateRepository candidateRepository, S3Client s3Client) {
    this.repository = candidateRepository;
    this.s3Client = s3Client;
    this.cristinMapper = CristinMapper.withDepartmentTransfers(readCristinDepartments());
  }

  @Override
  public Void handleRequest(SQSEvent sqsEvent, Context context) {

    sqsEvent.getRecords().stream().map(SQSMessage::getBody).forEach(this::processMessageBody);

    return null;
  }

  private DbCandidate createDbCandidate(CristinNviReport cristinNviReport) {
    return attempt(() -> cristinMapper.toDbCandidate(cristinNviReport))
        .orElseThrow(CristinConversionException::fromFailure);
  }

  private List<DbApprovalStatus> createApprovals(CristinNviReport cristinNviReport) {
    return attempt(() -> cristinMapper.toApprovals(cristinNviReport))
        .orElseThrow(CristinConversionException::fromFailure);
  }

  /**
   * Method is needed to wrap exception thrown by processBody() to Optional. This allows to persist
   * report for single entry.
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
      return repository
          .findByPublicationId(createPublicationId(cristinNviReport.publicationIdentifier()))
          .orElseGet(() -> createAndPersist(cristinNviReport));
    } catch (Exception e) {
      ErrorReport.withMessage(e.getMessage())
          .bucket(eventReference.extractBucketName())
          .key(cristinNviReport.publicationIdentifier())
          .persist(s3Client);
      throw CristinEventConsumerException.withMessage(e.getMessage());
    }
  }

  private CristinNviReport createNviReport(EventReference eventReference) {
    return attempt(() -> fetchS3Content(eventReference))
        .map(this::toCristinNviReport)
        .orElseThrow();
  }

  private CristinNviReport toCristinNviReport(String value) {
    return attempt(() -> dtoObjectMapper.readValue(value, CristinNviReport.class)).orElseThrow();
  }

  private String fetchS3Content(EventReference eventReference) {
    return new S3Driver(s3Client, eventReference.extractBucketName())
        .readEvent(eventReference.getUri());
  }

  private CandidateDao createAndPersist(CristinNviReport cristinNviReport) {
    var approvals = createApprovals(cristinNviReport);
    var candidate = createDbCandidate(cristinNviReport);
    var yearReported = cristinNviReport.yearReported();

    if (isNull(yearReported)) {
      throw new IllegalArgumentException(MISSING_REPORTED_YEAR_MESSAGE);
    } else {
      return repository.create(candidate, approvals, yearReported);
    }
  }

  private List<CristinDepartmentTransfer> readCristinDepartments() {
    try (StringReader reader = new StringReader(CRISTIN_DEPARTMENT_TRANSFERS_STRING)) {
      MappingIterator<CristinDepartmentTransfer> iterator =
          new CsvMapper()
              .readerFor(CristinDepartmentTransfer.class)
              .with(CsvSchema.emptySchema().withHeader())
              .readValues(reader);
      return iterator.readAll();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static URI createPublicationId(String publicationIdentifier) {
    return UriWrapper.fromHost(API_HOST)
        .addChild(PUBLICATION)
        .addChild(publicationIdentifier)
        .getUri();
  }
}
