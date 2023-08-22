package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.extractNviInstitutionIds;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.mapToVerifiedCreators;
import static no.sikt.nva.nvi.test.TestUtils.randomPublicationDate;
import static no.sikt.nva.nvi.test.TestUtils.toPublicationDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.NviPeriod;
import no.sikt.nva.nvi.common.model.business.Username;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.common.model.events.CandidateStatus;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails.Creator;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails.PublicationDate;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NviServiceTest {

    private NviService nviService;

    private FakeNviCandidateRepository fakeNviCandidateRepository;

    @BeforeEach
    void setup() {
        //TODO: Replace fakeNviCandidateRepository with actual repository when implemented
        fakeNviCandidateRepository = new FakeNviCandidateRepository();
        nviService = new NviService(fakeNviCandidateRepository);
    }

    @Test
    void shouldCreateCandidateWithPendingInstitutionApprovals() {
        var identifier = UUID.randomUUID();
        var verifiedCreators = List.of(new Creator(randomUri(), List.of(randomUri())));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = randomPublicationDate();
        var evaluatedCandidateDto = createEvaluatedCandidateDto(identifier, verifiedCreators, instanceType, randomLevel,
                                                                publicationDate);

        nviService.upsertCandidate(evaluatedCandidateDto);

        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate);
        assertThat(fakeNviCandidateRepository.findByPublicationId(expectedCandidate.publicationId()),
                   is(equalTo(Optional.of(expectedCandidate))));
    }

    //TODO: Change test when nviService is implemented
    @Test
    void shouldCreateNviPeriod() {
        var period = createPeriod();
        var persistedPeriod = nviService.createPeriod(period);
        assertThat(nviService.getPeriod(period.publishingYear()), is(not(equalTo(period))));
    }

    private static NviPeriod createPeriod() {
        var start = randomInstant();
        return new NviPeriod.Builder()
                   .withReportingDate(start)
                   .withPublishingYear(randomString())
                   .withCreatedBy(randomUsername())
                   .build();
    }

    private static Username randomUsername() {
        return new Username(randomString());
    }

    private CandidateEvaluatedMessage createEvaluatedCandidateDto(UUID identifier,
                                                                  List<CandidateDetails.Creator> creators,
                                                                  String instanceType, Level randomLevel,
                                                                  PublicationDate publicationDate) {
        return new CandidateEvaluatedMessage.Builder()
                   .withStatus(CandidateStatus.CANDIDATE)
                   .withPublicationBucketUri(generateS3BucketUri(identifier))
                   .withCandidateDetails(new CandidateDetails(generatePublicationId(identifier),
                                                              instanceType,
                                                              randomLevel.getValue(),
                                                              publicationDate,
                                                              creators))
                   .build();
    }

    private Candidate createExpectedCandidate(UUID identifier, List<CandidateDetails.Creator> creators,
                                              String instanceType,
                                              Level level, PublicationDate publicationDate) {
        return new Candidate.Builder()
                   .withPublicationId(generatePublicationId(identifier))
                   .withCreators(mapToVerifiedCreators(creators))
                   .withInstanceType(instanceType)
                   .withLevel(level)
                   .withIsApplicable(true)
                   .withPublicationDate(toPublicationDate(publicationDate))
                   .withApprovalStatuses(extractNviInstitutionIds(creators)
                                             .map(TestUtils::createPendingApprovalStatus)
                                             .toList())
                   .build();
    }
}