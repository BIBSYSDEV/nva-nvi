package no.sikt.nva.nvi.common.service;

import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import java.net.URI;
import java.util.List;
import java.util.Optional;
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
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NviServiceTest {

    public static final String BUCKET_HOST = "example.org";
    public static final String CANDIDATE = "Candidate";
    private static final String PUBLICATION_API_PATH = "publication";
    private static final Environment ENVIRONMENT = new Environment();
    private static final String API_HOST = ENVIRONMENT.readEnv("API_HOST");
    private NviService nviService;

    private FakeNviCandidateRepository fakeNviCandidateRepository;

    @BeforeEach
    void setup() {
        //TODO: Replace fakeNviCandidateRepository with actual repository when implemented
        fakeNviCandidateRepository = new FakeNviCandidateRepository();
        nviService = new NviService(fakeNviCandidateRepository);
    }

    //    @Test
    //    void shouldReturnCandidateIfCandidateIfCandidateWithIdExists() {
    //        var publicationId = generatePublicationId(UUID.randomUUID());
    //        var expectedCandidate = new Candidate.Builder()
    //                                    .withPublicationId(publicationId)
    //                                    .build();
    //        fakeNviCandidateRepository.save(expectedCandidate);
    //        assertThat(nviService.getCandidateByPublicationId(publicationId),
    //                   is(equalTo(Optional.of(expectedCandidate))));
    //    }
    //
    //    @Test
    //    void shouldReturnTrueIfCandidateIfCandidateWithIdExists() {
    //        var publicationId = generatePublicationId(UUID.randomUUID());
    //        var expectedCandidate = new Candidate.Builder()
    //                                    .withPublicationId(publicationId)
    //                                    .build();
    //        fakeNviCandidateRepository.save(expectedCandidate);
    //        assertThat(nviService.exists(publicationId), is(equalTo(true)));
    //    }

    @Test
    void shouldCreateCandidateWithPendingInstitutionApprovals() {
        var identifier = UUID.randomUUID();
        var verifiedCreators = List.of(
            new VerifiedCreatorDto(randomUri(), List.of(randomUri())));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = randomPublicationDate();
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate);

        var evaluatedCandidate =
            new EvaluatedCandidateDto(generateS3BucketUri(identifier).toString(),
                                      CANDIDATE,
                                      new CandidateDetailsDto(generatePublicationId(identifier),
                                                              instanceType,
                                                              randomLevel.getValue(),
                                                              publicationDate,
                                                              verifiedCreators));

        nviService.upsertCandidate(evaluatedCandidate);

        assertThat(fakeNviCandidateRepository.findByPublicationId(expectedCandidate.publicationId()),
                   is(equalTo(Optional.of(expectedCandidate))));
    }

    private static ApprovalStatus createPendingApprovalStatus(URI institutionUri) {
        return new ApprovalStatus.Builder()
                   .withStatus(Status.PENDING)
                   .withInstitutionId(institutionUri)
                   .build();
    }

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

    private URI generateS3BucketUri(UUID identifier) {
        return UriWrapper.fromHost(BUCKET_HOST).addChild(identifier.toString()).getUri();
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
                                             .map(NviServiceTest::createPendingApprovalStatus)
                                             .toList())
                   .build();
    }

    private URI generatePublicationId(UUID identifier) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(PUBLICATION_API_PATH)
                   .addChild(identifier.toString())
                   .getUri();
    }
}