package no.sikt.nva.nvi.events.cristin;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.AFFILIATION_DELIMITER;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.API_HOST;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.PERSISTED_RESOURCES_BUCKET;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import java.time.ZoneOffset;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
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
        var cristinNviReport = randomCristinNviReport();
        handler.handleRequest(createEvent(cristinNviReport), CONTEXT);
        var publicationId = toPublicationId(cristinNviReport);
        var nviCandidate = Candidate.fetchByPublicationId(() -> publicationId, candidateRepository, periodRepository);

        assertThatNviCandidateHasExpectedValues(nviCandidate, cristinNviReport);
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
                   is(equalTo(toPublicationDate(cristinNviReport.publicationDate()))));
        assertThat(candidate.getPublicationDetails().publicationId(),
                   is(equalTo(expectedPublicationId(cristinNviReport.publicationIdentifier()))));
        assertThat(candidate.getPublicationDetails().publicationBucketUri(),
                   is(equalTo(expectedPublicationBucketUri(cristinNviReport.publicationIdentifier()))));
        assertThat(candidate.isApplicable(), is(true));
        candidate.getApprovals()
            .values()
            .stream()
            .map(Approval::getStatus)
            .forEach(status -> assertThat(status, is(equalTo(ApprovalStatus.APPROVED))));
    }

    private URI expectedPublicationBucketUri(String value) {
        return UriWrapper.fromHost(PERSISTED_RESOURCES_BUCKET).addChild("resources").addChild(value + ".gz").getUri();
    }

    private URI expectedPublicationId(String value) {
        return UriWrapper.fromHost(API_HOST).addChild("publication").addChild(value).getUri();
    }

    private PublicationDate toPublicationDate(Instant instant) {
        var zonedDateTime = instant.atZone(ZoneOffset.UTC.getRules().getOffset(Instant.now()));
        return new PublicationDate(String.valueOf(zonedDateTime.getYear()), String.valueOf(zonedDateTime.getMonth()),
                                   String.valueOf(zonedDateTime.getDayOfMonth()));
    }

    private List<URI> generateExpectedApprovalsIds(CristinNviReport cristinNviReport) {
        return cristinNviReport.nviReport()
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

    private CristinNviReport randomCristinNviReport() {
        return CristinNviReport.builder()
                   .withCristinIdentifier(randomString())
                   .withPublicationIdentifier(randomUUID().toString())
                   .withYearReported(Integer.parseInt(randomYear()))
                   .withPublicationDate(randomInstant())
                   .withNviReport(List.of(randomCristinLocale()))
                   .build();
    }

    private CristinLocale randomCristinLocale() {
        return CristinLocale.builder()
                   .withDateControlled(DATE_CONTROLLED)
                   .withControlStatus(STATUS_CONTROLLED)
                   .withInstitutionIdentifier(randomString())
                   .withDepartmentIdentifier(randomString())
                   .withOwnerCode(randomString())
                   .withGroupIdentifier(randomString())
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