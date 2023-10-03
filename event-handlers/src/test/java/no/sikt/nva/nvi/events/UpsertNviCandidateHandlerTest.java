package no.sikt.nva.nvi.events;

import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceTypeExcluding;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.paths.UriWrapper.HTTPS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.NviApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto.Status;
import no.sikt.nva.nvi.events.CandidateDetails.Creator;
import no.sikt.nva.nvi.events.CandidateDetails.PublicationDate;
import no.sikt.nva.nvi.events.model.InvalidNviMessageException;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class UpsertNviCandidateHandlerTest extends LocalDynamoTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid";
    private static final Environment ENVIRONMENT = new Environment();
    private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
    private static final String CANDIDATE_PATH = "candidate";
    private UpsertNviCandidateHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = new PeriodRepository(localDynamo);
        handler = new UpsertNviCandidateHandler(candidateRepository, periodRepository);
    }

    @Test
    void shouldLogErrorWhenMessageBodyInvalid() {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var sqsEvent = createEventWithInvalidBody();

        handler.handleRequest(sqsEvent, CONTEXT);
        assertThat(appender.getMessages(), containsString(ERROR_MESSAGE_BODY_INVALID));
    }

    @ParameterizedTest
    @MethodSource("invalidCandidateEvaluatedMessages")
    void shouldThrowExceptionWhenInvalidMessage(CandidateEvaluatedMessage message) {
        var sqsEvent = createEvent(message);
        assertThrows(InvalidNviMessageException.class, () -> handler.handleRequest(sqsEvent, CONTEXT));
    }

    @Test
    void shouldSaveNewNviCandidateWithPendingInstitutionApprovalsIfCandidateDoesNotExist() {
        var institutionId = randomUri();
        var identifier = UUID.randomUUID();
        var creators = List.of(new Creator(randomUri(), List.of(institutionId)));
        var instanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var publicationId = generatePublicationId(identifier);
        var publicationBucketUri = generateS3BucketUri(identifier);

        var sqsEvent = createEvent(creators, instanceType, randomLevel, publicationDate, institutionPoints,
                                   publicationId, publicationBucketUri);
        handler.handleRequest(sqsEvent, CONTEXT);

        var fetchedCandidate = CandidateBO.fromRequest(() -> publicationId, candidateRepository,
                                                       periodRepository).toDto();
        var expectedResponse = createResponse(fetchedCandidate.identifier(), publicationId,
                                              institutionPoints);

        assertThat(fetchedCandidate, is(equalTo(expectedResponse)));
    }

    @Test
    void shouldUpdateExistingNviCandidateToNonCandidateWhenIncomingEventIsNonCandidate() {
        var dto = CandidateBO.fromRequest(
            createUpsertCandidateRequest(randomUri()), candidateRepository, periodRepository).orElseThrow().toDto();
        var eventMessage = nonCandidateMessageForExistingCandidate(dto);
        handler.handleRequest(createEvent(eventMessage), CONTEXT);
        var updatedCandidate =
            CandidateBO.fromRequest(dto::identifier, candidateRepository, periodRepository);

        assertThat(updatedCandidate.isApplicable(), is(false));
    }

    @Test
    void shouldUpdateNviCandidateAndDeleteInstitutionApprovalsIfCriticalCandidateDetailsAreChanged() {
        var keep = randomUri();
        var delete = randomUri();
        var identifier = UUID.randomUUID();
        var publicationId = generatePublicationId(identifier);
        var dto = CandidateBO.fromRequest(createUpsertCandidateRequest(publicationId, true, 1,
                                                                       InstanceType.ACADEMIC_ARTICLE, delete, keep),
                                          candidateRepository, periodRepository).orElseThrow();
        var sqsEvent = createEvent(keep, publicationId, generateS3BucketUri(identifier));
        handler.handleRequest(sqsEvent, CONTEXT);
        Map<URI, DbApprovalStatus> approvals = getAprovalMaps(dto);
        assertTrue(approvals.containsKey(keep));
        assertFalse(approvals.containsKey(delete));
        assertThat(approvals.size(), is(2));
    }

    private static Stream<CandidateEvaluatedMessage> invalidCandidateEvaluatedMessages() {
        return Stream.of(CandidateEvaluatedMessage.builder()
                             .withStatus(randomElement(CandidateStatus.values()))
                             .withPublicationBucketUri(randomUri())
                             .withCandidateDetails(new CandidateDetails(null,
                                                                        randomString(),
                                                                        randomElement(DbLevel.values()).getValue(),
                                                                        randomPublicationDate(),
                                                                        List.of(randomCreator()))).build(),
                         CandidateEvaluatedMessage.builder()
                             .withStatus(randomElement(CandidateStatus.values()))
                             .withPublicationBucketUri(null)
                             .withCandidateDetails(new CandidateDetails(randomUri(),
                                                                        randomString(),
                                                                        randomElement(DbLevel.values()).getValue(),
                                                                        randomPublicationDate(),
                                                                        List.of(randomCreator()))).build()
        );
    }

    private static CandidateDetails.PublicationDate randomPublicationDate() {
        var randomDate = randomLocalDate();
        return new CandidateDetails.PublicationDate(String.valueOf(randomDate.getYear()),
                                                    String.valueOf(randomDate.getMonthValue()),
                                                    String.valueOf(randomDate.getDayOfMonth()));
    }

    private static SQSEvent createEventWithInvalidBody() {
        var sqsEvent = new SQSEvent();
        var invalidSqsMessage = new SQSMessage();
        invalidSqsMessage.setBody(randomString());
        sqsEvent.setRecords(List.of(invalidSqsMessage));
        return sqsEvent;
    }

    private static CandidateDetails.Creator randomCreator() {
        return new CandidateDetails.Creator(randomUri(), List.of(randomUri()));
    }

    private static CandidateEvaluatedMessage createEvalMessage(List<Creator> verifiedCreators,
                                                               InstanceType instanceType, DbLevel randomLevel,
                                                               PublicationDate publicationDate,
                                                               Map<URI, BigDecimal> institutionPoints,
                                                               URI publicationId, URI publicationBucketUri) {
        return CandidateEvaluatedMessage.builder()
                   .withStatus(CandidateStatus.CANDIDATE)
                   .withPublicationBucketUri(publicationBucketUri)
                   .withCandidateDetails(new CandidateDetails(publicationId,
                                                              instanceType.getValue(),
                                                              randomLevel.getValue(),
                                                              publicationDate,
                                                              verifiedCreators))
                   .withInstitutionPoints(institutionPoints)
                   .build();
    }

    private Map<URI, DbApprovalStatus> getAprovalMaps(CandidateBO dto) {
        return candidateRepository.fetchApprovals(dto.identifier())
                   .stream()
                   .map(ApprovalStatusDao::approvalStatus)
                   .collect(Collectors.toMap(
                       DbApprovalStatus::institutionId, Function.identity()));
    }

    private CandidateDto createResponse(UUID identifier, URI publicationId, Map<URI, BigDecimal> institutionPoints) {
        var id = new UriWrapper(HTTPS, API_DOMAIN)
                     .addChild(BASE_PATH, CANDIDATE_PATH, identifier.toString())
                     .getUri();
        return CandidateDto.builder()
                   .withIdentifier(identifier)
                   .withPublicationId(publicationId)
                   .withId(id)
                   .withNotes(List.of())
                   .withApprovalStatuses(institutionPoints.entrySet().stream().map(this::mapToApprovalStatus).toList())
                   .withPeriodStatus(PeriodStatusDto.builder().withStatus(Status.NO_PERIOD).build())
                   .build();
    }

    private ApprovalStatus mapToApprovalStatus(Entry<URI, BigDecimal> e) {
        return ApprovalStatus.builder()
                   .withInstitutionId(e.getKey())
                   .withStatus(NviApprovalStatus.PENDING)
                   .withPoints(e.getValue())
                   .build();
    }

    private CandidateEvaluatedMessage nonCandidateMessageForExistingCandidate(CandidateDto candidate) {
        return CandidateEvaluatedMessage.builder()
                   .withStatus(CandidateStatus.NON_CANDIDATE)
                   .withPublicationBucketUri(generateS3BucketUri(candidate.identifier()))
                   .withInstitutionPoints(null)
                   .withCandidateDetails(new CandidateDetails(
                       candidate.publicationId(),
                       null, null, new PublicationDate(Year.now().toString(), null, null), null))
                   .build();
    }

    private SQSEvent createEvent(CandidateEvaluatedMessage candidateEvaluatedMessage) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        var body = attempt(() -> objectMapper.writeValueAsString(candidateEvaluatedMessage)).orElseThrow();
        message.setBody(body);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    private SQSEvent createEvent(URI keep, URI publicationId, URI publicationBucketUri) {
        var institutionId = randomUri();
        var creators = List.of(new Creator(randomUri(), List.of(institutionId, keep)));
        var instanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal(), keep, randomBigDecimal());

        return createEvent(creators, instanceType, randomLevel, publicationDate,
                           institutionPoints, publicationId, publicationBucketUri);
    }

    private SQSEvent createEvent(List<Creator> verifiedCreators,
                                 InstanceType instanceType,
                                 DbLevel randomLevel,
                                 PublicationDate publicationDate,
                                 Map<URI, BigDecimal> institutionPoints, URI publicationId, URI publicationBucketUri) {
        return createEvent(
            createEvalMessage(verifiedCreators, instanceType, randomLevel, publicationDate,
                              institutionPoints, publicationId, publicationBucketUri));
    }
}
