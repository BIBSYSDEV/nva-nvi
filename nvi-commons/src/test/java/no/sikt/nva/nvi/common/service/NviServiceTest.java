package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.extractNviInstitutionIds;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.mapToVerifiedCreators;
import static no.sikt.nva.nvi.test.TestUtils.randomPublicationDate;
import static no.sikt.nva.nvi.test.TestUtils.toPublicationDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.dao.Candidate;
import no.sikt.nva.nvi.common.model.dao.Level;
import no.sikt.nva.nvi.common.model.dto.CandidateDetailsDto;
import no.sikt.nva.nvi.common.model.dto.EvaluatedCandidateDto;
import no.sikt.nva.nvi.common.model.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.model.dto.VerifiedCreatorDto;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NviServiceTest {

    public static final String CANDIDATE = "Candidate";

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
        var verifiedCreators = List.of(new VerifiedCreatorDto(randomUri(), List.of(randomUri())));
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

    private EvaluatedCandidateDto createEvaluatedCandidateDto(UUID identifier,
                                                              List<VerifiedCreatorDto> verifiedCreators,
                                                              String instanceType, Level randomLevel,
                                                              PublicationDateDto publicationDate) {
        return new EvaluatedCandidateDto(generateS3BucketUri(identifier),
                                         CANDIDATE,
                                         new CandidateDetailsDto(generatePublicationId(identifier),
                                                                 instanceType,
                                                                 randomLevel.getValue(),
                                                                 publicationDate,
                                                                 verifiedCreators));
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