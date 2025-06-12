package no.sikt.nva.nvi.events.persist;

import static no.sikt.nva.nvi.common.UpsertRequestBuilder.fromRequest;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.unverifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.common.dto.PointCalculationDtoBuilder.randomPointCalculationDtoBuilder;
import static no.sikt.nva.nvi.common.dto.PublicationDetailsDtoBuilder.randomPublicationDetailsDtoBuilder;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomTopLevelOrganization;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.getRandomDateInCurrentYearAsDto;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.CandidateFixtures;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import nva.commons.core.Environment;
import nva.commons.logutils.LogUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings("PMD.CouplingBetweenObjects")
class UpsertNviCandidateHandlerTest {

  public static final Context CONTEXT = mock(Context.class);
  public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid";
  private static final String DLQ_QUEUE_URL = "test_dlq_url";
  private TestScenario scenario;
  private UpsertNviCandidateHandler handler;
  private CandidateRepository candidateRepository;
  private PeriodRepository periodRepository;
  private QueueClient queueClient;
  private Environment environment;

  @BeforeEach
  void setup() {
    scenario = new TestScenario();
    candidateRepository = scenario.getCandidateRepository();
    periodRepository = scenario.getPeriodRepository();
    setupOpenPeriod(scenario, CURRENT_YEAR);
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

  @Test
  void shouldSendMessageToDlqWhenMessageInvalid() {
    var invalidMessage = new CandidateEvaluatedMessage(new UpsertNonNviCandidateRequest(null));
    handler.handleRequest(createEvent(invalidMessage), CONTEXT);
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
    var expectedApprovals = getExpectedApprovals(evaluatedNviCandidate);
    var sqsEvent = createEvent(createEvalMessage(evaluatedNviCandidate));

    handler.handleRequest(sqsEvent, CONTEXT);

    var actualCandidate =
        scenario.getCandidateByPublicationId(evaluatedNviCandidate.publicationId());
    var actualApprovals = actualCandidate.getApprovals();
    Assertions.assertThat(actualApprovals)
        .hasSize(expectedApprovals.size())
        .allSatisfy(
            (id, approval) -> {
              Assertions.assertThat(approval)
                  .extracting(Approval::getInstitutionId, Approval::getStatus)
                  .contains(id, ApprovalStatus.PENDING);
            });
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
    candidate.updateApprovalStatus(
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
    var unverifiedCreator = new UnverifiedNviCreatorDto(randomString(), List.of(randomUri()));
    var evaluatedNviCandidate =
        randomEvaluatedNviCandidate().withNviCreators(unverifiedCreator).build();

    var sqsEvent = createEvent(createEvalMessage(evaluatedNviCandidate));
    handler.handleRequest(sqsEvent, CONTEXT);

    var actualCandidate =
        scenario.getCandidateByPublicationId(evaluatedNviCandidate.publicationId());
    var actualNviCreators = actualCandidate.getPublicationDetails().allCreators();
    Assertions.assertThat(actualNviCreators)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(List.of(unverifiedCreator));
  }

  @Test
  void shouldSaveNewNviCandidateWithBothVerifiedAndUnverifiedCreators() {
    var verifiedCreator = verifiedNviCreatorDtoFrom(randomUri());
    var unverifiedCreator = unverifiedNviCreatorDtoFrom(randomUri());
    var evaluatedNviCandidate =
        randomEvaluatedNviCandidate().withNviCreators(verifiedCreator, unverifiedCreator).build();
    var expectedNviCreators = List.of(verifiedCreator, unverifiedCreator);

    var sqsEvent = createEvent(createEvalMessage(evaluatedNviCandidate));
    handler.handleRequest(sqsEvent, CONTEXT);

    var actualCandidate =
        scenario.getCandidateByPublicationId(evaluatedNviCandidate.publicationId());
    var actualNviCreators = actualCandidate.getPublicationDetails().allCreators();
    Assertions.assertThat(actualNviCreators)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedNviCreators);
  }

  @Test
  void shouldUpdateExistingNviCandidateWhenUnverifiedCreatorIsAdded() {
    var requestBuilder = randomEvaluatedNviCandidate();
    var originalCandidate = scenario.upsertCandidate(requestBuilder.build());

    var updatedCreators = new ArrayList<>(originalCandidate.getPublicationDetails().allCreators());
    updatedCreators.add(unverifiedNviCreatorDtoFrom(randomUri()));
    var updateRequest = requestBuilder.withNviCreators(updatedCreators).build();

    var sqsEvent = createEvent(createEvalMessage(updateRequest));
    handler.handleRequest(sqsEvent, CONTEXT);

    var updatedCandidate = scenario.getCandidateByPublicationId(updateRequest.publicationId());
    var actualNviCreators = updatedCandidate.getPublicationDetails().allCreators();
    Assertions.assertThat(actualNviCreators)
        .hasSize(2)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(updatedCreators);
  }

  private static CandidateEvaluatedMessage randomCandidateEvaluatedMessage() {
    return createEvalMessage(randomEvaluatedNviCandidate().build());
  }

  private static UpsertRequestBuilder randomEvaluatedNviCandidate() {
    return randomUpsertRequestBuilder();
  }

  private static UpsertRequestBuilder getBuilder(
      URI publicationId, URI publicationBucketUri, VerifiedNviCreatorDto creator) {
    var publicationDetails = randomPublicationDetailsDtoBuilder().withId(publicationId).build();
    var topLevelOrganization = randomTopLevelOrganization();
    var subOrganization = topLevelOrganization.hasPart().getFirst();
    var pointCalculation =
        randomPointCalculationDtoBuilder()
            .withAdditionalPointFor(topLevelOrganization.id(), subOrganization.id(), creator.id())
            .build();
    return randomUpsertRequestBuilder()
        .withPublicationBucketUri(publicationBucketUri)
        .withPointCalculation(pointCalculation)
        .withPublicationDetails(publicationDetails)
        .withNviCreators(creator)
        .withTopLevelOrganizations(topLevelOrganization);
  }

  private static SQSEvent createEventWithInvalidBody() {
    var sqsEvent = new SQSEvent();
    var invalidSqsMessage = new SQSMessage();
    invalidSqsMessage.setBody(randomString());
    sqsEvent.setRecords(List.of(invalidSqsMessage));
    return sqsEvent;
  }

  private static CandidateEvaluatedMessage createEvalMessage(
      UpsertNviCandidateRequest nviCandidate) {
    return CandidateEvaluatedMessage.builder().withCandidateType(nviCandidate).build();
  }

  private List<DbApprovalStatus> getExpectedApprovals(
      UpsertNviCandidateRequest evaluatedNviCandidate) {
    return evaluatedNviCandidate.pointCalculation().institutionPoints().stream()
        .map(
            institution ->
                DbApprovalStatus.builder()
                    .institutionId(institution.institutionId())
                    .status(DbStatus.PENDING)
                    .build())
        .toList();
  }

  private UpsertNviCandidateRequest createNewUpsertRequestNotAffectingApprovals(
      UpsertNviCandidateRequest request) {
    return fromRequest(request).withPublicationDate(getRandomDateInCurrentYearAsDto()).build();
  }

  private CandidateEvaluatedMessage nonCandidateMessageForExistingCandidate(Candidate candidate) {
    return CandidateEvaluatedMessage.builder()
        .withCandidateType(new UpsertNonNviCandidateRequest(candidate.getPublicationId()))
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
    var creator = verifiedNviCreatorDtoFrom(someOtherInstitutionId);
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
                .withPoints(institutionPoints)
                .build()));
  }
}
