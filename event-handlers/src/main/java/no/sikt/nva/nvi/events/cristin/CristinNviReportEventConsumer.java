package no.sikt.nva.nvi.events.cristin;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.common.utils.Validator.isMissing;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.StringUtils.isNotBlank;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.model.DbOrganization;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.service.PublicationLoaderService;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

public class CristinNviReportEventConsumer implements RequestHandler<SQSEvent, Void> {

  public static final String NVI_ERRORS = "NVI_ERRORS";
  public static final String MISSING_REPORTED_YEAR_MESSAGE = "Reported year is missing!";
  public static final String CRISTIN_DEPARTMENT_TRANSFERS = "cristin_transfer_departments.csv";
  public static final String CRISTIN_DEPARTMENT_TRANSFERS_STRING =
      IoUtils.stringFromResources(Path.of(CRISTIN_DEPARTMENT_TRANSFERS));
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private final CandidateRepository repository;
  private final S3Client s3Client;
  private final CristinMapper cristinMapper;
  private final PublicationLoaderService publicationLoader;
  private final Logger logger = LoggerFactory.getLogger(CristinNviReportEventConsumer.class);

  @JacocoGenerated
  public CristinNviReportEventConsumer() {
    this(
        new CandidateRepository(defaultDynamoClient()),
        S3Driver.defaultS3Client().build(),
        new Environment());
  }

  public CristinNviReportEventConsumer(
      CandidateRepository candidateRepository, S3Client s3Client, Environment environment) {
    this.repository = candidateRepository;
    this.s3Client = s3Client;
    this.cristinMapper =
        CristinMapper.withDepartmentTransfers(readCristinDepartments(), environment);
    this.publicationLoader =
        new PublicationLoaderService(
            new S3StorageReader(s3Client, environment.readEnv(EXPANDED_RESOURCES_BUCKET)));
  }

  @Override
  public Void handleRequest(SQSEvent sqsEvent, Context context) {
    logger.info("Received {} messages from SQS", sqsEvent.getRecords().size());
    sqsEvent.getRecords().stream().map(SQSMessage::getBody).forEach(this::processMessageBody);
    logger.info("Finished processing messages from SQS");
    return null;
  }

  private DbCandidate createDbCandidate(CristinNviReport cristinNviReport) {
    var historicalCandidate =
        attempt(() -> cristinMapper.toDbCandidate(cristinNviReport))
            .orElseThrow(CristinConversionException::fromFailure);
    var publication =
        publicationLoader.extractAndTransform(historicalCandidate.publicationBucketUri());
    return createUpdatedDbCandidate(historicalCandidate, publication);
  }

  /**
   * This uses the current version of the publication to add data missing from the imported result.
   *
   * @param historicalCandidate DbCandidate object based entirely on imported data (as reported)
   * @param publication PublicationDto based on current version of persisted publication
   * @return A DbCandidate with all required fields set
   */
  private DbCandidate createUpdatedDbCandidate(
      DbCandidate historicalCandidate, PublicationDto publication) {
    var historicalDetails = historicalCandidate.publicationDetails();

    var currentTopLevelOrganizations = getCurrentTopLevelOrganizations(publication);
    var updatedPublicationDetails =
        historicalDetails
            .copy()
            .abstractText(
                firstValidValue(historicalDetails.abstractText(), publication.abstractText()))
            .title(firstValidValue(historicalDetails.title(), publication.title()))
            .language(firstValidValue(historicalDetails.language(), publication.language()))
            .status(firstValidValue(historicalDetails.status(), publication.status()))
            .identifier(firstValidValue(historicalDetails.identifier(), publication.identifier()))
            .topLevelNviOrganizations(currentTopLevelOrganizations)
            .build();
    return historicalCandidate.copy().publicationDetails(updatedPublicationDetails).build();
  }

  private List<DbOrganization> getCurrentTopLevelOrganizations(PublicationDto publication) {
    return isMissing(publication.topLevelOrganizations())
        ? createEmptyListForImportedResultsThatLackData(publication)
        : publication.topLevelOrganizations().stream().map(Organization::toDbOrganization).toList();
  }

  private List<DbOrganization> createEmptyListForImportedResultsThatLackData(
      PublicationDto publication) {
    logger.error(
        "Missing top level organizations for publication with identifier {}",
        publication.identifier());
    return emptyList();
  }

  private String firstValidValue(String originalValue, String updatedValue) {
    return isNotBlank(originalValue) ? originalValue : updatedValue;
  }

  private List<DbApprovalStatus> createApprovals(CristinNviReport cristinNviReport) {
    return attempt(() -> cristinMapper.toApprovals(cristinNviReport))
        .orElseThrow(CristinConversionException::fromFailure);
  }

  /**
   * Method is needed to wrap exception thrown by processBody() to Optional. This allows to persist
   * report for single entry.
   *
   * @param value The string value of event body
   */
  private void processMessageBody(String value) {
    try {
      processBody(value);
    } catch (Exception e) {
      logger.error("Failed to process message: {}", value, e);
    }
  }

  private void processBody(String value) {
    var eventReference = EventReference.fromJson(value);
    var cristinNviReport = createNviReport(eventReference);
    var publicationId = cristinMapper.constructPublicationId(cristinNviReport);
    try {
      repository
          .findByPublicationId(publicationId)
          .ifPresentOrElse(
              existingCandidate ->
                  logger.info("Candidate already exists for publicationId={}", publicationId),
              () -> createAndPersist(cristinNviReport));

      var existingCandidate = repository.findByPublicationId(publicationId);
      if (existingCandidate.isEmpty()) {
        createAndPersist(cristinNviReport);
      }
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

  private void createAndPersist(CristinNviReport cristinNviReport) {
    logger.info(
        "Processing CristinNviReport with publication identifier: {}",
        cristinNviReport.publicationIdentifier());
    var approvals = createApprovals(cristinNviReport);
    var candidate = createDbCandidate(cristinNviReport);
    var yearReported = cristinNviReport.getYearReportedFromHistoricalData();
    if (yearReported.isEmpty()) {
      throw new IllegalArgumentException(MISSING_REPORTED_YEAR_MESSAGE);
    } else {
      logger.info(
          "Persisting imported NVI result with identifier: {}",
          cristinNviReport.publicationIdentifier());
      repository.create(candidate, approvals, yearReported.get());
    }
  }

  /**
   * Reads the CSV file containing mappings of transferred department identifiers. The CSV file is
   * used to map creators' affiliations to their corresponding institutions in cases where the
   * department has been transferred from one institution to another. This ensures that institution
   * points are correctly calculated based on the updated affiliations.
   */
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
}
