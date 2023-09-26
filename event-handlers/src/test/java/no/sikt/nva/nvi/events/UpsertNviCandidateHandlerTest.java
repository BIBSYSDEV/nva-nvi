package no.sikt.nva.nvi.events;

import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateWithPublicationYear;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceTypeExcluding;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.model.CandidateDao.CandidateData;
import no.sikt.nva.nvi.common.db.model.CandidateDao.Creator;
import no.sikt.nva.nvi.common.db.model.CandidateDao.InstitutionPoints;
import no.sikt.nva.nvi.common.db.model.CandidateDao.ChannelLevel;
import no.sikt.nva.nvi.common.db.model.CandidateDao.PublicationDate;
import no.sikt.nva.nvi.common.db.model.CandidateDao.InstanceType;
import no.sikt.nva.nvi.common.service.Candidate;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.events.model.InvalidNviMessageException;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class UpsertNviCandidateHandlerTest extends LocalDynamoTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid";
    public static final int YEAR = Calendar.getInstance().getWeekYear();
    private UpsertNviCandidateHandler handler;
    private NviService nviService;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        nviService = TestUtils.nviServiceReturningOpenPeriod(localDynamo, YEAR);
        handler = new UpsertNviCandidateHandler(nviService);
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
        var identifier = UUID.randomUUID();
        var institutionId = randomUri();
        var creators = List.of(new CandidateDetails.Creator(randomUri(), List.of(institutionId)));
        var instanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var randomLevel = randomElement(ChannelLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());

        var sqsEvent = createEvent(identifier, creators, instanceType, randomLevel, publicationDate, institutionPoints);
        handler.handleRequest(sqsEvent, CONTEXT);

        var expectedCandidate = createExpectedCandidate(identifier, creators, instanceType, randomLevel,
                                                        publicationDate, institutionPoints);
        var fetchedCandidate = nviService.findByPublicationId(expectedCandidate.publicationId())
                                   .map(Candidate::candidate);

        assertThat(fetchedCandidate.orElseThrow(), is(equalTo(expectedCandidate)));
    }

    @Test
    void shouldUpdateExistingNviCandidateToNonCandidateWhenIncomingEventIsNonCandidate() {
        var existingCandidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();
        var eventMessage = nonCandidateMessageForExistingCandidate(existingCandidate);
        handler.handleRequest(createEvent(eventMessage), CONTEXT);
        var updatedCandidate = nviService.findCandidateById(existingCandidate.identifier()).orElseThrow();

        assertThat(updatedCandidate.candidate().applicable(), is(false));
        assertThat(updatedCandidate.candidate().instanceType(), is(InstanceType.NON_CANDIDATE));
    }

    private static PublicationDate toPublicationDate(CandidateDetails.PublicationDate publicationDate) {
        return new PublicationDate(publicationDate.year(),
                                   publicationDate.month(),
                                   publicationDate.day());
    }

    private static Stream<CandidateEvaluatedMessage> invalidCandidateEvaluatedMessages() {
        return Stream.of(CandidateEvaluatedMessage.builder()
                             .withStatus(randomElement(CandidateStatus.values()))
                             .withPublicationBucketUri(randomUri())
                             .withCandidateDetails(new CandidateDetails(null,
                                                                        randomString(),
                                                                        randomElement(ChannelLevel.values()).getValue(),
                                                                        randomPublicationDate(),
                                                                        List.of(randomCreator()))).build(),
                         CandidateEvaluatedMessage.builder()
                             .withStatus(randomElement(CandidateStatus.values()))
                             .withPublicationBucketUri(null)
                             .withCandidateDetails(new CandidateDetails(randomUri(),
                                                                        randomString(),
                                                                        randomElement(ChannelLevel.values()).getValue(),
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

    //TODO: shouldUpdateNviCandidateAndDeleteInstitutionApprovalsIfCriticalCandidateDetailsAreChanged

    //TODO: shouldMarkCandidateAsNotApplicableIfExistingCandidateBecomesNonCandidate

    private static CandidateDetails.Creator randomCreator() {
        return new CandidateDetails.Creator(randomUri(), List.of(randomUri()));
    }

    private static List<InstitutionPoints> mapToInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
        return institutionPoints.entrySet()
                   .stream()
                   .map(entry -> new InstitutionPoints(entry.getKey(), entry.getValue()))
                   .toList();
    }

    private static List<Creator> mapToVerifiedCreators(List<CandidateDetails.Creator> creators) {
        return creators.stream()
                   .map(creator -> new Creator(creator.id(), creator.nviInstitutions()))
                   .toList();
    }

    private static SQSEvent createEvent(CandidateEvaluatedMessage candidateEvaluatedMessage) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        var body = attempt(() -> objectMapper.writeValueAsString(candidateEvaluatedMessage)).orElseThrow();
        message.setBody(body);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    private CandidateEvaluatedMessage nonCandidateMessageForExistingCandidate(Candidate candidate) {
        return CandidateEvaluatedMessage.builder()
                   .withStatus(CandidateStatus.NON_CANDIDATE)
                   .withPublicationBucketUri(candidate.candidate().publicationBucketUri())
                   .withInstitutionPoints(null)
                   .withCandidateDetails(new CandidateDetails(candidate.candidate().publicationId(),
                                                              null, null, null, null))
                   .build();
    }

    private SQSEvent createEvent(UUID identifier,
                                 List<CandidateDetails.Creator> verifiedCreators,
                                 InstanceType instanceType,
                                 ChannelLevel randomLevel,
                                 CandidateDetails.PublicationDate publicationDate,
                                 Map<URI, BigDecimal> institutionPoints) {
        return createEvent(CandidateEvaluatedMessage.builder()
                               .withStatus(CandidateStatus.CANDIDATE)
                               .withPublicationBucketUri(generateS3BucketUri(identifier))
                               .withCandidateDetails(new CandidateDetails(generatePublicationId(identifier),
                                                                          instanceType.getValue(),
                                                                          randomLevel.getValue(),
                                                                          publicationDate,
                                                                          verifiedCreators))
                               .withInstitutionPoints(institutionPoints)
                               .build());
    }

    private CandidateData createExpectedCandidate(UUID identifier,
                                                  List<CandidateDetails.Creator> creators,
                                                  InstanceType instanceType,
                                                  ChannelLevel level,
                                                  CandidateDetails.PublicationDate publicationDate,
                                                  Map<URI, BigDecimal> institutionPoints) {
        return CandidateData.builder()
                   .publicationBucketUri(generateS3BucketUri(identifier))
                   .publicationId(generatePublicationId(identifier))
                   .creators(mapToVerifiedCreators(creators))
                   .instanceType(instanceType)
                   .level(level)
                   .applicable(true)
                   .publicationDate(toPublicationDate(publicationDate))
                   .points(mapToInstitutionPoints(institutionPoints))
                   .build();
    }
}
