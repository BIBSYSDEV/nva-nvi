package no.sikt.nva.nvi.events.persist;

import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceType;
import static no.sikt.nva.nvi.test.TestUtils.randomLevelExcluding;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.net.URI;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate.Builder;
import no.sikt.nva.nvi.events.model.NviCandidate.NviCreator;
import no.sikt.nva.nvi.events.model.PublicationDate;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import nva.commons.core.Environment;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class UpsertNviCandidateHandlerTest extends LocalDynamoTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid";
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
    void shouldSaveNewNviCandidateWithPendingInstitutionApprovalsWhenCandidateDoesNotExist() {
        var evaluatedNviCandidate = randomEvaluatedNviCandidate().build();
        var sqsEvent = createEvent(createEvalMessage(evaluatedNviCandidate));
        handler.handleRequest(sqsEvent, CONTEXT);
        var actualPersistedCandidateDao = candidateRepository.findByPublicationId(evaluatedNviCandidate.publicationId())
                                              .orElseThrow();
        var actualApprovals = fetchApprovals(actualPersistedCandidateDao);
        assertEquals(getExpectedApprovals(evaluatedNviCandidate), actualApprovals);
        assertEquals(getExpectedCandidate(evaluatedNviCandidate), actualPersistedCandidateDao.candidate());
    }

    @Test
    void shouldUpdateExistingNviCandidateToNonCandidateWhenIncomingEventIsNonCandidate() {
        var dto = Candidate.upsert(createUpsertCandidateRequest(randomUri()), candidateRepository,
                                   periodRepository).orElseThrow().toDto();
        var eventMessage = nonCandidateMessageForExistingCandidate(dto);
        handler.handleRequest(createEvent(eventMessage), CONTEXT);
        var updatedCandidate = Candidate.fetch(dto::identifier, candidateRepository, periodRepository);

        assertThat(updatedCandidate.isApplicable(), is(false));
    }

    @Test
    void shouldUpdateNviCandidateAndDeleteInstitutionApprovalsIfCriticalCandidateDetailsAreChanged() {
        var keep = randomUri();
        var delete = randomUri();
        var identifier = UUID.randomUUID();
        var publicationId = generatePublicationId(identifier);
        var dto = Candidate.upsert(
            createUpsertCandidateRequest(publicationId, randomUri(), true, InstanceType.ACADEMIC_ARTICLE, 1,
                                         randomBigDecimal(),
                                         randomLevelExcluding(DbLevel.NON_CANDIDATE).getValue(),
                                         TestUtils.CURRENT_YEAR,
                                         delete, keep),
            candidateRepository, periodRepository).orElseThrow();
        var sqsEvent = createEvent(keep, publicationId, generateS3BucketUri(identifier));
        handler.handleRequest(sqsEvent, CONTEXT);
        var approvals = getApprovalMaps(dto);
        assertTrue(approvals.containsKey(keep));
        assertFalse(approvals.containsKey(delete));
        assertThat(approvals.size(), is(2));
    }

    @Test
    void shouldNotResetApprovalsWhenUpdatingFieldsNotEffectingApprovals() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidate = Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();
        candidate.updateApproval(
            new UpdateStatusRequest(institutionId, ApprovalStatus.APPROVED, randomString(), randomString()));
        var approval = candidate.toDto().approvals().get(0);
        var newUpsertRequest = createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest, institutionId);
        var updatedCandidate = Candidate.upsert(newUpsertRequest, candidateRepository, periodRepository)
                                   .orElseThrow();
        var updatedApproval = updatedCandidate.toDto().approvals().get(0);

        assertThat(updatedApproval, is(equalTo(approval)));
    }

    private static CandidateEvaluatedMessage randomCandidateEvaluatedMessage() {
        return createEvalMessage(randomEvaluatedNviCandidate().build());
    }

    private static NviCandidate.Builder randomEvaluatedNviCandidate() {
        var identifier = UUID.randomUUID();
        var publicationId = generatePublicationId(identifier);
        var publicationBucketUri = generateS3BucketUri(identifier);
        var creator = randomCreator();
        return getBuilder(publicationId, publicationBucketUri, creator);
    }

    private static Builder getBuilder(URI publicationId, URI publicationBucketUri, NviCreator creator) {
        return NviCandidate.builder()
                   .withPublicationId(publicationId)
                   .withPublicationBucketUri(publicationBucketUri)
                   .withInstanceType(randomInstanceType())
                   .withLevel(randomElement(DbLevel.values()).getValue())
                   .withTotalPoints(randomBigDecimal(4))
                   .withBasePoints(randomBigDecimal(4))
                   .withCreatorShareCount(randomInteger())
                   .withCollaborationFactor(randomBigDecimal(4))
                   .withPublicationChannelId(randomUri())
                   .withChannelType(randomElement(ChannelType.values()).getValue())
                   .withIsInternationalCollaboration(randomBoolean())
                   .withInstitutionPoints(List.of(new InstitutionPoints(randomUri(), randomBigDecimal(4),
                                                                        List.of(new CreatorAffiliationPoints(
                                                                            creator.id(), randomUri(),
                                                                            randomBigDecimal())))))
                   .withDate(randomPublicationDate())
                   .withVerifiedCreators(List.of(creator));
    }

    private static Stream<CandidateEvaluatedMessage> invalidCandidateEvaluatedMessages() {
        return Stream.of(createEvalMessage(NviCandidate.builder()
                                               .withPublicationId(null)
                                               .build()));
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

    private static NviCreator randomCreator() {
        return new NviCreator(randomUri(), List.of(randomUri()));
    }

    private static CandidateEvaluatedMessage createEvalMessage(NviCandidate nviCandidate) {
        return CandidateEvaluatedMessage.builder()
                   .withCandidateType(nviCandidate)
                   .build();
    }

    private static List<DbInstitutionPoints> mapToInstitutionPoints(List<InstitutionPoints> institutionPoints) {
        return institutionPoints
                   .stream()
                   .map(DbInstitutionPoints::from)
                   .toList();
    }

    private List<DbApprovalStatus> fetchApprovals(CandidateDao actualPersistedCandidateDao) {
        return candidateRepository.fetchApprovals(actualPersistedCandidateDao.identifier())
                   .stream()
                   .map(ApprovalStatusDao::approvalStatus)
                   .toList();
    }

    private List<DbApprovalStatus> getExpectedApprovals(NviCandidate evaluatedNviCandidate) {
        return evaluatedNviCandidate.institutionPoints().stream()
                   .map(institution -> DbApprovalStatus.builder()
                                           .institutionId(institution.institutionId())
                                           .status(DbStatus.PENDING)
                                           .build())
                   .toList();
    }

    private UpsertCandidateRequest createNewUpsertRequestNotAffectingApprovals(UpsertCandidateRequest request,
                                                                               URI institutionId) {
        var creatorId = request.creators().keySet().stream().toList().get(0);
        var creator = new NviCreator(creatorId, List.of(institutionId));
        return getBuilder(request.publicationId(), request.publicationBucketUri(), creator)
                   .withInstanceType(request.instanceType())
                   .withLevel(request.level())
                   .withDate(new PublicationDate(null, "3", Year.now().toString()))
                   .withVerifiedCreators(List.of(new NviCreator(creatorId, List.of(institutionId))))
                   .withInstitutionPoints(request.institutionPoints())
                   .build();
    }

    private Map<URI, DbApprovalStatus> getApprovalMaps(Candidate dto) {
        return candidateRepository.fetchApprovals(dto.getIdentifier())
                   .stream()
                   .map(ApprovalStatusDao::approvalStatus)
                   .collect(Collectors.toMap(DbApprovalStatus::institutionId, Function.identity()));
    }

    private DbCandidate getExpectedCandidate(NviCandidate evaluatedNviCandidate) {
        var date = evaluatedNviCandidate.date();
        return DbCandidate.builder()
                   .applicable(true)
                   .publicationId(evaluatedNviCandidate.publicationId())
                   .publicationBucketUri(evaluatedNviCandidate.publicationBucketUri())
                   .instanceType(evaluatedNviCandidate.instanceType().getValue())
                   .level(DbLevel.parse(evaluatedNviCandidate.level()))
                   .publicationDate(new DbPublicationDate(date.year(), date.month(), date.day()))
                   .channelType(ChannelType.parse(evaluatedNviCandidate.channelType()))
                   .channelId(evaluatedNviCandidate.publicationChannelId())
                   .creators(getExpectedCreators(evaluatedNviCandidate))
                   .basePoints(evaluatedNviCandidate.basePoints())
                   .internationalCollaboration(evaluatedNviCandidate.isInternationalCollaboration())
                   .collaborationFactor(evaluatedNviCandidate.collaborationFactor())
                   .creatorShareCount(evaluatedNviCandidate.creatorShareCount())
                   .points(mapToInstitutionPoints(evaluatedNviCandidate.institutionPoints()))
                   .totalPoints(evaluatedNviCandidate.totalPoints())
                   .build();
    }

    private List<DbCreator> getExpectedCreators(NviCandidate evaluatedNviCandidate) {
        return evaluatedNviCandidate.creators().entrySet().stream()
                   .map(entry -> DbCreator.builder().creatorId(entry.getKey())
                                     .affiliations(new ArrayList<>(entry.getValue()))
                                     .build())
                   .toList();
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

    private SQSEvent createEvent(URI affiliationId, URI publicationId, URI publicationBucketUri) {
        var someOtherInstitutionId = randomUri();
        var creator = new NviCreator(randomUri(), List.of(affiliationId, someOtherInstitutionId));
        var institutionPoints =
            List.of(new InstitutionPoints(affiliationId, randomBigDecimal(), List.of(
                        new CreatorAffiliationPoints(creator.id(), affiliationId, randomBigDecimal()))),
                    new InstitutionPoints(someOtherInstitutionId, randomBigDecimal(),
                                          List.of(new CreatorAffiliationPoints(creator.id(), someOtherInstitutionId,
                                                                               randomBigDecimal()))));
        return createEvent(createEvalMessage(
            getBuilder(publicationId, publicationBucketUri, creator).withInstitutionPoints(institutionPoints).build()));
    }
}
