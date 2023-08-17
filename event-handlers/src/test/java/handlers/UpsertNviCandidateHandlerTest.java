package handlers;

import static no.sikt.nva.nvi.test.TestUtils.extractNviInstitutionIds;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.mapToVerifiedCreators;
import static no.sikt.nva.nvi.test.TestUtils.randomPublicationDate;
import static no.sikt.nva.nvi.test.TestUtils.toPublicationDate;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.dao.Candidate;
import no.sikt.nva.nvi.common.model.dao.Level;
import no.sikt.nva.nvi.common.model.dto.CandidateDetailsDto;
import no.sikt.nva.nvi.common.model.dto.EvaluatedCandidateDto;
import no.sikt.nva.nvi.common.model.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.model.dto.VerifiedCreatorDto;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.test.TestUtils;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UpsertNviCandidateHandlerTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid";
    public static final String CANDIDATE = "Candidate";
    FakeNviCandidateRepository fakeNviCandidateRepository;
    private UpsertNviCandidateHandler handler;

    @BeforeEach
    void setup() {
        //TODO: Replace fakeNviCandidateRepository with actual repository when implemented
        fakeNviCandidateRepository = new FakeNviCandidateRepository();
        NviService nviService = new NviService(fakeNviCandidateRepository);
        handler = new UpsertNviCandidateHandler(nviService);
    }

    @Test
    void shouldLogErrorWhenMessageBodyInvalid() {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var sqsEvent = createEventWithInvalidBody();

        handler.handleRequest(sqsEvent, CONTEXT);
        assertThat(appender.getMessages(), containsString(ERROR_MESSAGE_BODY_INVALID));
    }

    @Test
    void shouldLogErrorWhenMessageBodyPublicationBucketUriNull() {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var sqsEvent = createEvent(new EvaluatedCandidateDto(null, null, null));

        handler.handleRequest(sqsEvent, CONTEXT);

        assertThat(appender.getMessages(), containsString(ERROR_MESSAGE_BODY_INVALID));
    }

    @Test
    void shouldSaveNewNviCandidateWithPendingInstitutionApprovalsIfCandidateDoesNotExist() {
        var identifier = UUID.randomUUID();
        var verifiedCreators = List.of(
            new VerifiedCreatorDto(randomUri(), List.of(randomUri())));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = randomPublicationDate();

        var sqsEvent = createEvent(identifier, verifiedCreators, instanceType, randomLevel, publicationDate);
        handler.handleRequest(sqsEvent, CONTEXT);

        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate);
        assertThat(
            fakeNviCandidateRepository.findByPublicationId(expectedCandidate.publicationId()).orElse(null),
            is(equalTo(expectedCandidate)));
    }

    //TODO: shouldUpdateNviCandidateAndDeleteInstitutionApprovalsIfCriticalCandidateDetailsAreChanged

    //TODO: shouldMarkCandidateAsNotApplicableIfExistingCandidateBecomesNonCandidate

    private static SQSEvent createEventWithInvalidBody() {
        var sqsEvent = new SQSEvent();
        var invalidSqsMessage = new SQSMessage();
        invalidSqsMessage.setBody(randomString());
        sqsEvent.setRecords(List.of(invalidSqsMessage));
        return sqsEvent;
    }

    private static SQSEvent createEvent(EvaluatedCandidateDto evaluatedCandidateDto) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        var body = attempt(() -> objectMapper.writeValueAsString(evaluatedCandidateDto)).orElseThrow();
        message.setBody(body);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    private SQSEvent createEvent(UUID identifier, List<VerifiedCreatorDto> verifiedCreators, String instanceType,
                                 Level randomLevel, PublicationDateDto publicationDate) {
        return createEvent(new EvaluatedCandidateDto(generateS3BucketUri(identifier),
                                                     CANDIDATE,
                                                     new CandidateDetailsDto(generatePublicationId(identifier),
                                                                             instanceType,
                                                                             randomLevel.getValue(),
                                                                             publicationDate,
                                                                             verifiedCreators)));
    }

    private Candidate createExpectedCandidate(UUID identifier, List<VerifiedCreatorDto> verifiedCreators,
                                              String instanceType,
                                              Level level, PublicationDateDto publicationDate) {
        return new Candidate.Builder()
                   .withPublicationId(generatePublicationId(identifier))
                   .withCreators(mapToVerifiedCreators(verifiedCreators))
                   .withInstanceType(instanceType)
                   .withLevel(level)
                   .withIsApplicable(true)
                   .withPublicationDate(toPublicationDate(publicationDate))
                   .withApprovalStatuses(extractNviInstitutionIds(verifiedCreators)
                                             .map(TestUtils::createPendingApprovalStatus)
                                             .toList())
                   .build();
    }
}
