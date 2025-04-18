package no.sikt.nva.nvi.events.persist;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.initializeTestDatabase;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.model.InstanceTypeFixtures.randomInstanceType;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.model.CandidateFixtures;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate.Builder;
import nva.commons.core.Environment;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings("PMD.CouplingBetweenObjects")
class UpsertNviCandidateHandlerTest {

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
    candidateRepository = new CandidateRepository(initializeTestDatabase());
    periodRepository =
        PeriodRepositoryFixtures.periodRepositoryReturningOpenedPeriod(Year.now().getValue());
    queueClient = mock(QueueClient.class);
    environment = mock(Environment.class);
    when(environment.readEnv("UPSERT_CANDIDATE_DLQ_QUEUE_URL")).thenReturn(DLQ_QUEUE_URL);
    handler =
        new UpsertNviCandidateHandler(
            candidateRepository, periodRepository, queueClient, environment);
  }

  @Test
  void shouldLogErrorWhenMessageBodyInvalid() {
    var appender = LogUtils.getTestingAppenderForRootLogger();
    var sqsEvent = createEventWithInvalidBody();

    handler.handleRequest(sqsEvent, CONTEXT);
    assertThat(appender.getMessages(), containsString(ERROR_MESSAGE_BODY_INVALID));
  }

  @ParameterizedTest(name = "should send message to DLQ when message invalid: {0}")
  @MethodSource("invalidCandidateEvaluatedMessages")
  void shouldSendMessageToDlqWhenMessageInvalid(CandidateEvaluatedMessage message) {
    handler.handleRequest(createEvent(message), CONTEXT);
    verify(queueClient, times(1)).sendMessage(any(String.class), eq(DLQ_QUEUE_URL));
  }

  @Test
  void shouldSendMessageToDlqWhenUnexpectedErrorOccurs() {
    candidateRepository = mock(CandidateRepository.class);
    handler =
        new UpsertNviCandidateHandler(
            candidateRepository, periodRepository, queueClient, environment);
    when(candidateRepository.create(any(), any())).thenThrow(RuntimeException.class);

    handler.handleRequest(createEvent(randomCandidateEvaluatedMessage()), CONTEXT);

    verify(queueClient, times(1)).sendMessage(any(String.class), eq(DLQ_QUEUE_URL));
  }

  @Test
  void shouldSaveNewNviCandidateWithPendingInstitutionApprovalsWhenCandidateDoesNotExist() {
    var evaluatedNviCandidate = randomEvaluatedNviCandidate().build();
    var sqsEvent = createEvent(createEvalMessage(evaluatedNviCandidate));
    handler.handleRequest(sqsEvent, CONTEXT);
    var actualPersistedCandidateDao =
        candidateRepository
            .findByPublicationId(evaluatedNviCandidate.publicationId())
            .orElseThrow();
    var actualApprovals = fetchApprovals(actualPersistedCandidateDao);
    assertEquals(getExpectedApprovals(evaluatedNviCandidate), actualApprovals);
    assertEquals(
        getExpectedCandidate(evaluatedNviCandidate), actualPersistedCandidateDao.candidate());
  }

  @Test
  void shouldUpdateExistingNviCandidateToNonCandidateWhenIncomingEventIsNonCandidate() {
    var candidate =
        CandidateFixtures.setupRandomApplicableCandidate(candidateRepository, periodRepository);
    var eventMessage = nonCandidateMessageForExistingCandidate(candidate);
    handler.handleRequest(createEvent(eventMessage), CONTEXT);
    var updatedCandidate =
        Candidate.fetch(candidate::getIdentifier, candidateRepository, periodRepository);

    assertThat(updatedCandidate.isApplicable(), is(false));
  }

  @Test
  void shouldUpdateNviCandidateAndDeleteInstitutionApprovalsIfCriticalCandidateDetailsAreChanged() {
    var keep = randomUri();
    var delete = randomUri();
    var identifier = UUID.randomUUID();
    var publicationId = generatePublicationId(identifier);
    var institutions = new URI[] {delete, keep};

    var request =
        createUpsertCandidateRequest(institutions).withPublicationId(publicationId).build();
    Candidate.upsert(request, candidateRepository, periodRepository);

    var sqsEvent = createEvent(keep, publicationId, generateS3BucketUri(identifier));
    handler.handleRequest(sqsEvent, CONTEXT);
    var approvals =
        Candidate.fetchByPublicationId(() -> publicationId, candidateRepository, periodRepository)
            .getApprovals();
    assertTrue(approvals.containsKey(keep));
    assertFalse(approvals.containsKey(delete));
    assertThat(approvals.size(), is(2));
  }

  @Test
  void shouldNotResetApprovalsWhenUpdatingFieldsNotEffectingApprovals() {
    var institutionId = randomUri();
    var upsertCandidateRequest = createUpsertCandidateRequest(institutionId).build();
    Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository);
    var candidate =
        Candidate.fetchByPublicationId(
            upsertCandidateRequest::publicationId, candidateRepository, periodRepository);
    candidate.updateApproval(
        new UpdateStatusRequest(
            institutionId, ApprovalStatus.APPROVED, randomString(), randomString()));
    var approval = candidate.getApprovals().get(institutionId);
    var newUpsertRequest = createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest);
    Candidate.upsert(newUpsertRequest, candidateRepository, periodRepository);
    var updatedCandidate =
        Candidate.fetchByPublicationId(
            newUpsertRequest::publicationId, candidateRepository, periodRepository);
    var updatedApproval = updatedCandidate.getApprovals().get(institutionId);

    assertThat(updatedApproval, is(equalTo(approval)));
  }

  @Test
  void shouldSaveNewNviCandidateWithOnlyUnverifiedCreators() {
    var unverifiedCreators =
        List.of(new UnverifiedNviCreatorDto(randomString(), List.of(randomUri())));
    var evaluatedNviCandidate =
        randomEvaluatedNviCandidate()
            .withVerifiedNviCreators(emptyList())
            .withUnverifiedNviCreators(unverifiedCreators)
            .build();
    var expectedCreatorCount = unverifiedCreators.size();

    var sqsEvent = createEvent(createEvalMessage(evaluatedNviCandidate));
    handler.handleRequest(sqsEvent, CONTEXT);
    var actualPersistedCandidateDao =
        candidateRepository
            .findByPublicationId(evaluatedNviCandidate.publicationId())
            .orElseThrow();

    assertEquals(expectedCreatorCount, actualPersistedCandidateDao.candidate().creators().size());
    assertEquals(
        getExpectedCandidate(evaluatedNviCandidate), actualPersistedCandidateDao.candidate());
  }

  @Test
  void shouldSaveNewNviCandidateWithBothVerifiedAndUnverifiedCreators() {
    var unverifiedCreators =
        List.of(new UnverifiedNviCreatorDto(randomString(), List.of(randomUri())));
    var evaluatedNviCandidate =
        randomEvaluatedNviCandidate().withUnverifiedNviCreators(unverifiedCreators).build();
    var expectedCreatorCount =
        evaluatedNviCandidate.verifiedCreators().size() + unverifiedCreators.size();

    var sqsEvent = createEvent(createEvalMessage(evaluatedNviCandidate));
    handler.handleRequest(sqsEvent, CONTEXT);
    var actualPersistedCandidateDao =
        candidateRepository
            .findByPublicationId(evaluatedNviCandidate.publicationId())
            .orElseThrow();

    assertEquals(expectedCreatorCount, actualPersistedCandidateDao.candidate().creators().size());
    assertEquals(
        getExpectedCandidate(evaluatedNviCandidate), actualPersistedCandidateDao.candidate());
  }

  @Test
  void shouldUpdateExistingNviCandidateWhenUnverifiedCreatorIsAdded() {
    var evaluatedNviCandidate = randomEvaluatedNviCandidate();
    var sqsEvent = createEvent(createEvalMessage(evaluatedNviCandidate.build()));
    handler.handleRequest(sqsEvent, CONTEXT);

    var unverifiedCreators =
        List.of(new UnverifiedNviCreatorDto(randomString(), List.of(randomUri())));
    var updatedEvaluatedNviCandidate =
        evaluatedNviCandidate.withUnverifiedNviCreators(unverifiedCreators).build();
    var expectedCreatorCount =
        updatedEvaluatedNviCandidate.verifiedCreators().size() + unverifiedCreators.size();

    sqsEvent = createEvent(createEvalMessage(updatedEvaluatedNviCandidate));
    handler.handleRequest(sqsEvent, CONTEXT);
    var actualPersistedCandidateDao =
        candidateRepository
            .findByPublicationId(updatedEvaluatedNviCandidate.publicationId())
            .orElseThrow();

    assertEquals(expectedCreatorCount, actualPersistedCandidateDao.candidate().creators().size());
    assertEquals(
        getExpectedCandidate(updatedEvaluatedNviCandidate),
        actualPersistedCandidateDao.candidate());
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

  private static Builder getBuilder(
      URI publicationId, URI publicationBucketUri, VerifiedNviCreatorDto creator) {
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
        .withInstitutionPoints(
            List.of(
                new InstitutionPoints(
                    randomUri(),
                    randomBigDecimal(4),
                    List.of(
                        new CreatorAffiliationPoints(
                            creator.id(), randomUri(), randomBigDecimal())))))
        .withDate(randomPublicationDate())
        .withVerifiedNviCreators(List.of(creator));
  }

  private static Stream<CandidateEvaluatedMessage> invalidCandidateEvaluatedMessages() {
    return Stream.of(createEvalMessage(NviCandidate.builder().withPublicationId(null).build()));
  }

  private static PublicationDateDto randomPublicationDate() {
    var randomDate = randomLocalDate();
    return new PublicationDateDto(
        String.valueOf(randomDate.getDayOfMonth()),
        String.valueOf(randomDate.getMonthValue()),
        String.valueOf(randomDate.getYear()));
  }

  private static SQSEvent createEventWithInvalidBody() {
    var sqsEvent = new SQSEvent();
    var invalidSqsMessage = new SQSMessage();
    invalidSqsMessage.setBody(randomString());
    sqsEvent.setRecords(List.of(invalidSqsMessage));
    return sqsEvent;
  }

  private static VerifiedNviCreatorDto randomCreator() {
    return new VerifiedNviCreatorDto(randomUri(), List.of(randomUri()));
  }

  private static CandidateEvaluatedMessage createEvalMessage(NviCandidate nviCandidate) {
    return CandidateEvaluatedMessage.builder().withCandidateType(nviCandidate).build();
  }

  private static List<DbInstitutionPoints> mapToInstitutionPoints(
      List<InstitutionPoints> institutionPoints) {
    return institutionPoints.stream().map(DbInstitutionPoints::from).toList();
  }

  private List<DbApprovalStatus> fetchApprovals(CandidateDao actualPersistedCandidateDao) {
    return candidateRepository.fetchApprovals(actualPersistedCandidateDao.identifier()).stream()
        .map(ApprovalStatusDao::approvalStatus)
        .toList();
  }

  private List<DbApprovalStatus> getExpectedApprovals(NviCandidate evaluatedNviCandidate) {
    return evaluatedNviCandidate.institutionPoints().stream()
        .map(
            institution ->
                DbApprovalStatus.builder()
                    .institutionId(institution.institutionId())
                    .status(DbStatus.PENDING)
                    .build())
        .toList();
  }

  private UpsertCandidateRequest createNewUpsertRequestNotAffectingApprovals(
      UpsertCandidateRequest request) {
    var creator = request.verifiedCreators().getFirst();
    return getBuilder(request.publicationId(), request.publicationBucketUri(), creator)
        .withInstanceType(request.instanceType())
        .withLevel(request.level())
        .withPublicationChannelId(request.publicationChannelId())
        .withDate(new PublicationDateDto(Year.now().toString(), "3", null))
        .withVerifiedNviCreators(List.of(creator))
        .withInstitutionPoints(request.institutionPoints())
        .build();
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

  private static List<DbCreatorType> getExpectedCreators(NviCandidate evaluatedNviCandidate) {
    Stream<DbCreatorType> verifiedCreators =
        evaluatedNviCandidate.verifiedCreators().stream().map(VerifiedNviCreatorDto::toDao);
    Stream<DbCreatorType> unverifiedCreators =
        evaluatedNviCandidate.unverifiedCreators().stream().map(UnverifiedNviCreatorDto::toDao);
    return Stream.concat(verifiedCreators, unverifiedCreators).toList();
  }

  private CandidateEvaluatedMessage nonCandidateMessageForExistingCandidate(Candidate candidate) {
    return CandidateEvaluatedMessage.builder()
        .withCandidateType(new NonNviCandidate(candidate.getPublicationId()))
        .build();
  }

  private SQSEvent createEvent(CandidateEvaluatedMessage candidateEvaluatedMessage) {
    var sqsEvent = new SQSEvent();
    var message = new SQSMessage();
    var body =
        attempt(() -> objectMapper.writeValueAsString(candidateEvaluatedMessage)).orElseThrow();
    message.setBody(body);
    sqsEvent.setRecords(List.of(message));
    return sqsEvent;
  }

  private SQSEvent createEvent(URI affiliationId, URI publicationId, URI publicationBucketUri) {
    var someOtherInstitutionId = randomUri();
    var creator =
        new VerifiedNviCreatorDto(randomUri(), List.of(affiliationId, someOtherInstitutionId));
    var institutionPoints =
        List.of(
            new InstitutionPoints(
                affiliationId,
                randomBigDecimal(),
                List.of(
                    new CreatorAffiliationPoints(creator.id(), affiliationId, randomBigDecimal()))),
            new InstitutionPoints(
                someOtherInstitutionId,
                randomBigDecimal(),
                List.of(
                    new CreatorAffiliationPoints(
                        creator.id(), someOtherInstitutionId, randomBigDecimal()))));
    return createEvent(
        createEvalMessage(
            getBuilder(publicationId, publicationBucketUri, creator)
                .withInstitutionPoints(institutionPoints)
                .build()));
  }
}
