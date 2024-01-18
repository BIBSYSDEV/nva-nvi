package no.sikt.nva.nvi.events.cristin;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.AFFILIATION_DELIMITER;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.API_HOST;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.PERSISTED_RESOURCES_BUCKET;
import static no.sikt.nva.nvi.events.cristin.CristinNviReportEventConsumer.PARSE_EVENT_BODY_ERROR_MESSAGE;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CristinNviReportEventConsumerTest extends LocalDynamoTest {

    public static final LocalDate DATE_CONTROLLED = LocalDate.now();
    public static final String STATUS_CONTROLLED = "J";
    public static final String PUBLICATION = "publication";
    private static final Context CONTEXT = mock(Context.class);
    private CristinNviReportEventConsumer handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = new PeriodRepository(localDynamo);
        handler = new CristinNviReportEventConsumer(candidateRepository);
    }

    @Test
    void shouldCreateNviCandidateFromNviReport() {
        var cristinNviReport = randomCristinNviReport();
        handler.handleRequest(createEvent(cristinNviReport), CONTEXT);
        var publicationId = toPublicationId(cristinNviReport);
        var nviCandidate = Candidate.fetchByPublicationId(() -> publicationId, candidateRepository, periodRepository);

        assertThatNviCandidateHasExpectedValues(nviCandidate, cristinNviReport);
    }

    @Test
    void shouldThrowRuntimeExceptionWithExpectedMessageWhenCanNotParseEventBody() {
        var eventBody = randomString();
        assertThrows(RuntimeException.class, () -> handler.handleRequest(eventWithBody(eventBody), CONTEXT),
                     PARSE_EVENT_BODY_ERROR_MESSAGE + eventBody);
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
        return UriWrapper.fromUri(PERSISTED_RESOURCES_BUCKET).addChild("resources").addChild(value).getUri();
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

    private SQSEvent createEvent(CristinNviReport cristinNviReport) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        var body = attempt(() -> objectMapper.writeValueAsString(cristinNviReport)).orElseThrow();
        message.setBody(body);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    private SQSEvent eventWithBody(String value) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        message.setBody(value);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }
}