package handlers;

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
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.model.dao.ApprovalStatus;
import no.sikt.nva.nvi.common.model.dao.Candidate;
import no.sikt.nva.nvi.common.model.dao.Level;
import no.sikt.nva.nvi.common.model.dao.PublicationDate;
import no.sikt.nva.nvi.common.model.dao.Status;
import no.sikt.nva.nvi.common.model.dao.VerifiedCreator;
import no.sikt.nva.nvi.common.model.dto.CandidateDetailsDto;
import no.sikt.nva.nvi.common.model.dto.EvaluatedCandidateDto;
import no.sikt.nva.nvi.common.model.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.model.dto.VerifiedCreatorDto;
import no.sikt.nva.nvi.common.service.NviService;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UpsertNviCandidateHandlerTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid";
    public static final String BUCKET_HOST = "example.org";
    public static final String PUBLICATION_API_PATH = "publication";
    public static final String CANDIDATE = "Candidate";
    private static final Environment ENVIRONMENT = new Environment();
    public static final String API_HOST = ENVIRONMENT.readEnv("API_HOST");
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
        var sqsEvent = createEventWithMessageBody(new EvaluatedCandidateDto(null, null, null));

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
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate);

        var sqsEvent = createEventWithMessageBody(
            new EvaluatedCandidateDto(generateS3BucketUri(identifier).toString(),
                                      CANDIDATE,
                                      new CandidateDetailsDto(generatePublicationId(identifier),
                                                              instanceType,
                                                              randomLevel.getValue(),
                                                              publicationDate,
                                                              verifiedCreators)));
        handler.handleRequest(sqsEvent, CONTEXT);

        assertThat(
            fakeNviCandidateRepository.findByPublicationId(expectedCandidate.publicationId()).orElse(null),
            is(equalTo(expectedCandidate)));
    }

    //TODO: shouldUpdateNviCandidateAndDeleteInstitutionApprovalsIfCriticalCandidateDetailsAreChanged

    //TODO: shouldMarkCandidateAsNotApplicableIfExistingCandidateBecomesNonCandidate

    private static PublicationDateDto randomPublicationDate() {
        var randomDate = randomLocalDate();
        return new PublicationDateDto(String.valueOf(randomDate.getYear()),
                                      String.valueOf(randomDate.getMonthValue()),
                                      String.valueOf(randomDate.getDayOfMonth()));
    }

    private static Stream<URI> extractNviInstitutionIds(List<VerifiedCreatorDto> creators) {
        return creators.stream()
                   .flatMap(creatorDto -> creatorDto.nviInstitutions().stream())
                   .distinct();
    }

    private static PublicationDate toPublicationDate(PublicationDateDto publicationDate) {
        return new PublicationDate(publicationDate.year(),
                                   publicationDate.month(),
                                   publicationDate.day());
    }

    private static List<VerifiedCreator> mapToVerifiedCreators(List<VerifiedCreatorDto> creatorDtos) {
        return creatorDtos.stream()
                   .map(creator -> new VerifiedCreator(creator.id(), creator.nviInstitutions()))
                   .toList();
    }

    private static SQSEvent createEventWithMessageBody(EvaluatedCandidateDto evaluatedCandidateDto) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        var body = attempt(() -> objectMapper.writeValueAsString(evaluatedCandidateDto)).orElseThrow();
        message.setBody(body);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    private static SQSEvent createEventWithInvalidBody() {
        var sqsEvent = new SQSEvent();
        var invalidSqsMessage = new SQSMessage();
        invalidSqsMessage.setBody(randomString());
        sqsEvent.setRecords(List.of(invalidSqsMessage));
        return sqsEvent;
    }

    private static ApprovalStatus createPendingApprovalStatus(URI institutionUri) {
        return new ApprovalStatus.Builder()
                   .withStatus(Status.PENDING)
                   .withInstitutionId(institutionUri)
                   .build();
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
                                             .map(
                                                 UpsertNviCandidateHandlerTest::createPendingApprovalStatus)
                                             .toList())
                   .build();
    }

    private URI generatePublicationId(UUID identifier) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(PUBLICATION_API_PATH)
                   .addChild(identifier.toString())
                   .getUri();
    }

    private URI generateS3BucketUri(UUID identifier) {
        return UriWrapper.fromHost(BUCKET_HOST).addChild(identifier.toString()).getUri();
    }
}
