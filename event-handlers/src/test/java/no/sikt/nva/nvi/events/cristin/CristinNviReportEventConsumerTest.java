package no.sikt.nva.nvi.events.cristin;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.AFFILIATION_DELIMITER;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.API_HOST;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.PERSISTED_RESOURCES_BUCKET;
import static no.sikt.nva.nvi.events.cristin.CristinNviReportEventConsumer.NVI_ERRORS;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.Creator;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.events.cristin.CristinNviReport.Builder;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CristinNviReportEventConsumerTest extends LocalDynamoTest {

    public static final LocalDate DATE_CONTROLLED = LocalDate.now();
    public static final String STATUS_CONTROLLED = "J";
    public static final String PUBLICATION = "publication";
    private static final String BUCKET_NAME = "not-important";
    private static final Context CONTEXT = mock(Context.class);
    private CristinNviReportEventConsumer handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private S3Driver s3Driver;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = new PeriodRepository(localDynamo);
        var s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, BUCKET_NAME);
        handler = new CristinNviReportEventConsumer(candidateRepository, s3Client);
    }

    @Test
    void shouldCreateNviCandidateFromNviReport() throws IOException {
        var cristinNviReport = randomCristinNviReport().build();
        periodRepository.save(periodForYear(cristinNviReport.yearReported()));
        handler.handleRequest(createEvent(cristinNviReport), CONTEXT);
        var publicationId = toPublicationId(cristinNviReport);
        var nviCandidate = Candidate.fetchByPublicationId(() -> publicationId, candidateRepository, periodRepository);

        assertThatNviCandidateHasExpectedValues(nviCandidate, cristinNviReport);
    }

    @Test
    void shouldStoreErrorReportWhenFailInCristinMapper() throws IOException {
        var cristinNviReport = nviReportWithIdentifier();
        handler.handleRequest(createEvent(cristinNviReport), CONTEXT);
        var s3ReportPath = UriWrapper.fromHost(BUCKET_NAME)
                               .addChild(NVI_ERRORS)
                               .addChild(cristinNviReport.publicationIdentifier())
                               .toS3bucketPath();
        var expectedReport = s3Driver.getFile(s3ReportPath);

        assertThat(expectedReport, containsString("Could not create nvi candidate"));
    }

    @Test
    void shouldStoreErrorReportWhenYearReportedIsMissing() throws IOException {
        var cristinNviReport = randomCristinNviReport().withYearReported(null).build();
        handler.handleRequest(createEvent(cristinNviReport), CONTEXT);
        var s3ReportPath = UriWrapper.fromHost(BUCKET_NAME)
                               .addChild(NVI_ERRORS)
                               .addChild(cristinNviReport.publicationIdentifier())
                               .toS3bucketPath();
        var expectedReport = s3Driver.getFile(s3ReportPath);

        assertThat(expectedReport, containsString("Reported year is missing!"));
    }

    private static DbNviPeriod periodForYear(String cristinNviReport) {
        return DbNviPeriod.builder()
                   .publishingYear(cristinNviReport)
                   .startDate(Instant.now().plusSeconds(10_000))
                   .reportingDate(Instant.now().plusSeconds(10_000_000))
                   .build();
    }

    private static URI toPublicationId(CristinNviReport cristinNviReport) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(PUBLICATION)
                   .addChild(cristinNviReport.publicationIdentifier())
                   .getUri();
    }

    private void assertThatNviCandidateHasExpectedValues(Candidate candidate, CristinNviReport cristinNviReport) {
        assertThat(candidate.getApprovals().keySet().stream().toList(),
                   containsInAnyOrder(generateExpectedApprovalsIds(cristinNviReport).toArray()));
        assertThat(candidate.getPublicationDetails().publicationDate(),
                   is(equalTo(cristinNviReport.publicationDate())));
        assertThat(candidate.getPublicationDetails().publicationId(),
                   is(equalTo(expectedPublicationId(cristinNviReport.publicationIdentifier()))));
        assertThat(candidate.getPublicationDetails().publicationBucketUri(),
                   is(equalTo(expectedPublicationBucketUri(cristinNviReport.publicationIdentifier()))));
        assertThat(candidate.isApplicable(), is(true));
        assertThat(candidate.getPeriod().year(), is(equalTo(String.valueOf(cristinNviReport.yearReported()))));
        assertThat(candidate.getPublicationDetails().level(), is(equalTo("LevelOne")));
        assertThat(candidate.getPublicationDetails().creators(),
                   Matchers.contains(constructExpectedCreator(cristinNviReport)));
        assertThat(candidate.getPublicationDetails().type(), is(equalTo(cristinNviReport.instanceType())));
        candidate.getApprovals()
            .values()
            .stream()
            .map(Approval::getStatus)
            .forEach(status -> assertThat(status, is(equalTo(ApprovalStatus.APPROVED))));
    }

    private Creator constructExpectedCreator(CristinNviReport cristinNviReport) {
        var creatorIdentifier = cristinNviReport.getCreators().getFirst().getCristinPersonIdentifier();
        var creatorId = UriWrapper.fromHost(API_HOST)
                .addChild("cristin")
                .addChild("person")
                .addChild(creatorIdentifier)
                .getUri();
        var affiliations = cristinNviReport.getCreators().stream()
                               .map(ScientificPerson::getOrganization)
                               .map(this::toOrganizationId)
                               .distinct().toList();
        return new Creator(creatorId, affiliations);
    }

    private URI expectedPublicationBucketUri(String value) {
        return UriWrapper.fromHost(PERSISTED_RESOURCES_BUCKET).addChild("resources").addChild(value + ".gz").getUri();
    }

    private URI expectedPublicationId(String value) {
        return UriWrapper.fromHost(API_HOST).addChild("publication").addChild(value).getUri();
    }

    private List<URI> generateExpectedApprovalsIds(CristinNviReport cristinNviReport) {
        return cristinNviReport.cristinLocales()
                   .stream()
                   .map(this::toOrganizationIdentifier)
                   .map(this::toOrganizationId)
                   .toList();
    }

    private URI toOrganizationId(String id) {
        return UriWrapper.fromHost(API_HOST).addChild("cristin").addChild("organization").addChild(id).getUri();
    }

    private String toOrganizationIdentifier(CristinLocale locale) {
        return locale.getInstitutionIdentifier()
               + AFFILIATION_DELIMITER
               + locale.getDepartmentIdentifier()
               + AFFILIATION_DELIMITER
               + locale.getSubDepartmentIdentifier()
               + AFFILIATION_DELIMITER
               + locale.getGroupIdentifier();
    }

    private Builder randomCristinNviReport() {
        var institutionIdentifier = randomString();
        return CristinNviReport.builder()
                   .withCristinIdentifier(randomString())
                   .withPublicationIdentifier(randomUUID().toString())
                   .withYearReported(randomYear())
                   .withPublicationDate(randomPublicationDate())
                   .withCristinLocales(List.of(randomCristinLocale(institutionIdentifier)))
                   .withScientificResources(List.of(scientificResource(institutionIdentifier)))
                   .withInstanceType(randomString())
                   .withReference(null);
    }

    private CristinNviReport nviReportWithIdentifier() {
        return CristinNviReport.builder()
                   .withPublicationIdentifier(randomString())
                   .build();
    }

    private ScientificResource scientificResource(String institutionIdentifier) {
        var cristinIdentifier = randomString();
        return ScientificResource.build()
                   .withQualityCode("1")
                   .withReportedYear(randomYear())
                   .withScientificPeople(List.of(scientificPersonWithCristinIdentifier(cristinIdentifier, institutionIdentifier),
                                                 scientificPersonWithCristinIdentifier(cristinIdentifier, institutionIdentifier)))
                   .build();
    }

    private ScientificPerson scientificPersonWithCristinIdentifier(String personIdentifier,
                                                                   String institutionIdentifier) {
        return ScientificPerson.builder()
                   .withCristinPersonIdentifier(personIdentifier)
                   .withInstitutionIdentifier(institutionIdentifier)
                   .withDepartmentIdentifier(randomString())
                   .withSubDepartmentIdentifier(randomString())
                   .withGroupIdentifier(randomString())
                   .withGroupIdentifier(randomString())
                   .withAuthorPointsForAffiliation("1.6")
                   .withCollaborationFactor("1.0")
                   .withPublicationTypeLevelPoints("1.5")
                   .build();
    }

    private static PublicationDate randomPublicationDate() {
        return new PublicationDate(randomString(),
                                   randomString(),
                                   randomString());
    }

    private CristinLocale randomCristinLocale(String institutionIdentifier) {
        return CristinLocale.builder()
                   .withDateControlled(DATE_CONTROLLED)
                   .withControlStatus(STATUS_CONTROLLED)
                   .withInstitutionIdentifier(institutionIdentifier)
                   .withDepartmentIdentifier(randomString())
                   .withOwnerCode(randomString())
                   .withGroupIdentifier(randomString())
                   .withControlledByUser(CristinUser.builder().withIdentifier(randomString()).build())
                   .build();
    }

    private SQSEvent createEvent(CristinNviReport cristinNviReport) throws IOException {
        var fullPath = UnixPath.of(randomString(), randomString());
        var fileUri = s3Driver.insertFile(fullPath, cristinNviReport.toJsonString());
        var eventReference = new EventReference(randomString(), randomString(), fileUri, Instant.now());
        s3Driver.insertFile(fullPath, cristinNviReport.toJsonString());
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        message.setBody(eventReference.toJsonString());
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }
}