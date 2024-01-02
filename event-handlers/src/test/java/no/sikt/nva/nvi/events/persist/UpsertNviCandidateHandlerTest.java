package no.sikt.nva.nvi.events.persist;

import static no.sikt.nva.nvi.common.db.model.InstanceType.NON_CANDIDATE;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceTypeExcluding;
import static no.sikt.nva.nvi.test.TestUtils.randomLevelExcluding;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.dto.ApprovalDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto.Status;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate.Creator;
import no.sikt.nva.nvi.events.model.NviCandidate.PublicationDate;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
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
    private static final String DLQ_QUEUE_URL = "test_dlq_url";
    private UpsertNviCandidateHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    private QueueClient queueClient;

    private Environment environment;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = TestUtils.periodRepositoryReturningOpenedPeriod(Year.now().getValue());
        queueClient = mock(QueueClient.class);
        environment = mock(Environment.class);
        when(environment.readEnv("UPSERT_CANDIDATE_DLQ_QUEUE_URL")).thenReturn(DLQ_QUEUE_URL);
        handler = new UpsertNviCandidateHandler(candidateRepository, periodRepository, queueClient, environment);
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
    void shouldSendMessageToDlqWhenMessageInvalid(CandidateEvaluatedMessage message) {
        handler.handleRequest(createEvent(message), CONTEXT);
        verify(queueClient, times(1)).sendMessage(any(String.class), eq(DLQ_QUEUE_URL));
    }

    @Test
    void shouldSendMessageToDlqWhenUnexpectedErrorOccurs() {
        candidateRepository = mock(CandidateRepository.class);
        handler = new UpsertNviCandidateHandler(candidateRepository, periodRepository, queueClient, environment);
        when(candidateRepository.create(any(), any())).thenThrow(RuntimeException.class);

        handler.handleRequest(createEvent(randomCandidateEvaluatedMessage()), CONTEXT);

        verify(queueClient, times(1)).sendMessage(any(String.class), eq(DLQ_QUEUE_URL));
    }

    @Test
    void shouldSaveNewNviCandidateWithPendingInstitutionApprovalsIfCandidateDoesNotExist() {
        var institutionId = randomUri();
        var identifier = UUID.randomUUID();
        var creators = List.of(new Creator(randomUri(), List.of(institutionId)));
        var instanceType = randomInstanceTypeExcluding(NON_CANDIDATE);
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var publicationId = generatePublicationId(identifier);
        var publicationBucketUri = generateS3BucketUri(identifier);
        var totalPoints = randomBigDecimal();

        var sqsEvent = createEvent(creators, instanceType, randomLevel, publicationDate, institutionPoints,
                                   publicationId, publicationBucketUri, totalPoints);
        handler.handleRequest(sqsEvent, CONTEXT);

        var fetchedCandidate = Candidate.fromRequest(() -> publicationId, candidateRepository, periodRepository)
                                   .toDto();
        var expectedResponse = createResponse(fetchedCandidate.identifier(), publicationId, institutionPoints,
                                              totalPoints);

        assertThat(fetchedCandidate, is(equalTo(expectedResponse)));
    }

    @Test
    void shouldUpdateExistingNviCandidateToNonCandidateWhenIncomingEventIsNonCandidate() {
        var dto = Candidate.fromRequest(createUpsertCandidateRequest(randomUri()), candidateRepository,
                                        periodRepository).orElseThrow().toDto();
        var eventMessage = nonCandidateMessageForExistingCandidate(dto);
        handler.handleRequest(createEvent(eventMessage), CONTEXT);
        var updatedCandidate = Candidate.fromRequest(dto::identifier, candidateRepository, periodRepository);

        assertThat(updatedCandidate.isApplicable(), is(false));
    }

    @Test
    void shouldUpdateNviCandidateAndDeleteInstitutionApprovalsIfCriticalCandidateDetailsAreChanged() {
        var keep = randomUri();
        var delete = randomUri();
        var identifier = UUID.randomUUID();
        var publicationId = generatePublicationId(identifier);
        var dto = Candidate.fromRequest(
            createUpsertCandidateRequest(publicationId, randomUri(), true, InstanceType.ACADEMIC_ARTICLE, 1,
                                         randomBigDecimal(),
                                         randomLevelExcluding(DbLevel.NON_CANDIDATE).getVersionOneValue(),
                                         TestUtils.CURRENT_YEAR,
                                         delete, keep),
            candidateRepository, periodRepository).orElseThrow();
        var sqsEvent = createEvent(keep, publicationId, generateS3BucketUri(identifier));
        handler.handleRequest(sqsEvent, CONTEXT);
        Map<URI, DbApprovalStatus> approvals = getAprovalMaps(dto);
        assertTrue(approvals.containsKey(keep));
        assertFalse(approvals.containsKey(delete));
        assertThat(approvals.size(), is(2));
    }

    @Test
    void shouldNotResetApprovalsWhenUpdatingFieldsNotEffectingApprovals() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidate = Candidate.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();
        candidate.updateApproval(
            new UpdateStatusRequest(institutionId, ApprovalStatus.APPROVED, randomString(), randomString()));
        var approval = candidate.toDto().approvals().get(0);
        var newUpsertRequest = createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest, institutionId);
        var updatedCandidate = Candidate.fromRequest(newUpsertRequest, candidateRepository, periodRepository)
                                   .orElseThrow();
        var updatedApproval = updatedCandidate.toDto().approvals().get(0);

        assertThat(updatedApproval, is(equalTo(approval)));
    }

    private static CandidateEvaluatedMessage randomCandidateEvaluatedMessage() {
        var identifier = UUID.randomUUID();
        return CandidateEvaluatedMessage.builder()
                   .withCandidateType(NviCandidate.builder()
                                          .withPublicationId(generatePublicationId(identifier))
                                          .withPublicationBucketUri(generateS3BucketUri(identifier))
                                          .withInstanceType(randomInstanceTypeExcluding(NON_CANDIDATE).getValue())
                                          .withLevel(randomElement(DbLevel.values()).getVersionOneValue())
                                          .withTotalPoints(randomBigDecimal())
                                          .withBasePoints(randomBigDecimal())
                                          .withCreatorShareCount(randomInteger())
                                          .withCollaborationFactor(randomBigDecimal())
                                          .withPublicationChannelId(randomUri())
                                          .withChannelType(randomElement(ChannelType.values()).getValue())
                                          .withIsInternationalCollaboration(randomBoolean())
                                          .withInstitutionPoints(Map.of(randomUri(), randomBigDecimal()))
                                          .withDate(randomPublicationDate())
                                          .withVerifiedCreators(List.of(randomCreator()))
                                          .build())
                   .build();
    }

    private static Stream<CandidateEvaluatedMessage> invalidCandidateEvaluatedMessages() {
        return Stream.of(CandidateEvaluatedMessage.builder()
                             .withCandidateType(NviCandidate.builder()
                                                    .withPublicationId(null)
                                                    .withInstanceType(randomString())
                                                    .withLevel(randomElement(
                                                        DbLevel.values()).getVersionOneValue())
                                                    .withDate(randomPublicationDate())
                                                    .withVerifiedCreators(List.of(randomCreator()))
                                                    .build())
                             .build());
    }

    private static PublicationDate randomPublicationDate() {
        var randomDate = randomLocalDate();
        return new PublicationDate(String.valueOf(randomDate.getYear()),
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

    private static Creator randomCreator() {
        return new Creator(randomUri(), List.of(randomUri()));
    }

    private static CandidateEvaluatedMessage createEvalMessage(List<Creator> verifiedCreators,
                                                               InstanceType instanceType, DbLevel level,
                                                               PublicationDate publicationDate,
                                                               Map<URI, BigDecimal> institutionPoints,
                                                               URI publicationId, URI publicationBucketUri,
                                                               BigDecimal totalPoints) {
        return CandidateEvaluatedMessage.builder()
                   .withCandidateType(NviCandidate.builder()
                                          .withPublicationId(publicationId)
                                          .withPublicationBucketUri(publicationBucketUri)
                                          .withInstanceType(instanceType.getValue())
                                          .withChannelType(randomElement(ChannelType.values()).getValue())
                                          .withPublicationChannelId(randomUri())
                                          .withLevel(level.getVersionOneValue())
                                          .withDate(publicationDate)
                                          .withVerifiedCreators(verifiedCreators)
                                          .withIsInternationalCollaboration(randomBoolean())
                                          .withCollaborationFactor(randomBigDecimal())
                                          .withCreatorShareCount(randomInteger())
                                          .withBasePoints(randomBigDecimal())
                                          .withTotalPoints(totalPoints)
                                          .withInstitutionPoints(institutionPoints)
                                          .build())
                   .build();
    }

    private static PeriodStatusDto toPeriodStatus(DbNviPeriod period) {
        return PeriodStatusDto.builder()
                   .withStatus(Status.OPEN_PERIOD)
                   .withStartDate(period.startDate().toString())
                   .withReportingDate(period.reportingDate().toString())
                   .build();
    }

    private UpsertCandidateRequest createNewUpsertRequestNotAffectingApprovals(UpsertCandidateRequest request,
                                                                               URI institutionId) {
        var creatorId = request.creators().keySet().stream().toList().get(0);
        return NviCandidate.builder()
                   .withPublicationId(request.publicationId())
                   .withPublicationBucketUri(request.publicationBucketUri())
                   .withInstanceType(request.instanceType())
                   .withLevel(request.level())
                   .withDate(
                       new PublicationDate(null, "3",
                                           Year.now().toString()))
                   .withVerifiedCreators(
                       List.of(new Creator(creatorId,
                                           List.of(institutionId))))
                   .withInstitutionPoints(request.institutionPoints())
                   .withTotalPoints(randomBigDecimal())
                   .build();
    }

    private Map<URI, DbApprovalStatus> getAprovalMaps(Candidate dto) {
        return candidateRepository.fetchApprovals(dto.getIdentifier())
                   .stream()
                   .map(ApprovalStatusDao::approvalStatus)
                   .collect(Collectors.toMap(DbApprovalStatus::institutionId, Function.identity()));
    }

    private CandidateDto createResponse(UUID identifier, URI publicationId, Map<URI, BigDecimal> institutionPoints,
                                        BigDecimal totalPoints) {
        var id = new UriWrapper(HTTPS, API_DOMAIN).addChild(BASE_PATH, CANDIDATE_PATH, identifier.toString()).getUri();
        var period = periodRepository.findByPublishingYear(Year.now().toString()).orElseThrow();
        return CandidateDto.builder()
                   .withIdentifier(identifier)
                   .withPublicationId(publicationId)
                   .withId(id)
                   .withNotes(List.of())
                   .withApprovalStatuses(institutionPoints.entrySet().stream().map(this::mapToApprovalStatus).toList())
                   .withPeriodStatus(toPeriodStatus(period))
                   .withTotalPoints(totalPoints)
                   .build();
    }

    private ApprovalDto mapToApprovalStatus(Entry<URI, BigDecimal> pointsMap) {
        return ApprovalDto.builder()
                   .withInstitutionId(pointsMap.getKey())
                   .withStatus(ApprovalStatus.PENDING)
                   .withPoints(pointsMap.getValue())
                   .build();
    }

    private CandidateEvaluatedMessage nonCandidateMessageForExistingCandidate(CandidateDto candidate) {
        return CandidateEvaluatedMessage.builder()
                   .withCandidateType(new NonNviCandidate(candidate.publicationId()))
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
        var instanceType = randomInstanceTypeExcluding(NON_CANDIDATE);
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal(), keep, randomBigDecimal());

        return createEvent(creators, instanceType, randomLevel, publicationDate, institutionPoints, publicationId,
                           publicationBucketUri, randomBigDecimal());
    }

    private SQSEvent createEvent(List<Creator> verifiedCreators, InstanceType instanceType, DbLevel randomLevel,
                                 PublicationDate publicationDate, Map<URI, BigDecimal> institutionPoints,
                                 URI publicationId, URI publicationBucketUri, BigDecimal totalPoints) {
        return createEvent(
            createEvalMessage(verifiedCreators, instanceType, randomLevel, publicationDate, institutionPoints,
                              publicationId, publicationBucketUri, totalPoints));
    }
}
