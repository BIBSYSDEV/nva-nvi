package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.ApplicationConstants.NVI_TABLE_NAME;
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
import java.util.UUID;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.common.model.events.CandidateStatus;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails.Creator;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails.PublicationDate;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

public class NviServiceTest extends LocalDynamoTest {

    private NviService nviService;

    private NviCandidateRepository nviCandidateRepository;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        nviCandidateRepository = new NviCandidateRepository(localDynamo);
        nviService = new NviService(nviCandidateRepository);
    }

    @Test
    void shouldCreateAndFetchPublicationById() {
        var identifier = UUID.randomUUID();
        var verifiedCreators = List.of(new Creator(randomUri(), List.of(randomUri())));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = randomPublicationDate();
        var evaluatedCandidateDto = createEvaluatedCandidateDto(identifier, verifiedCreators, instanceType, randomLevel,
                                                                publicationDate);

        var createdCandidate = nviService.upsertCandidate(evaluatedCandidateDto).get();
        var createdCandidateId = createdCandidate.identifier();

        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate);
        var fetchedCandidate = nviService.findById(createdCandidateId).get().candidate();

        assertThat(fetchedCandidate,
                   is(equalTo(expectedCandidate)));
    }

    @Test
    void shouldCreateAndFetchPublicationByPublicationId() {
        var identifier = UUID.randomUUID();
        var verifiedCreators = List.of(new Creator(randomUri(), List.of(randomUri())));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = randomPublicationDate();
        var evaluatedCandidateDto = createEvaluatedCandidateDto(identifier, verifiedCreators, instanceType, randomLevel,
                                                                publicationDate);

        nviService.upsertCandidate(evaluatedCandidateDto).get().identifier();

        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate);
        var fetchedCandidate = nviService.findByPublicationId(generatePublicationId(identifier)).get().candidate();

        assertThat(fetchedCandidate,
                   is(equalTo(expectedCandidate)));
    }

    @Test
    void shouldCreateUniquenessIdentifierWhenCreatingCandidate() {
        var identifier = UUID.randomUUID();
        var verifiedCreators = List.of(new Creator(randomUri(), List.of(randomUri())));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = randomPublicationDate();
        var evaluatedCandidateDto = createEvaluatedCandidateDto(identifier, verifiedCreators, instanceType, randomLevel,
                                                                publicationDate);
        nviService.upsertCandidate(evaluatedCandidateDto).get().identifier();

        var scan = this.localDynamo.scan(ScanRequest.builder().tableName(NVI_TABLE_NAME).build());
        var items = scan.items().size();

        assertThat(items,
                   is(equalTo(2)));
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
        var fetchedCandidate = nviCandidateRepository.findByPublicationId(generatePublicationId(identifier)).map(
            CandidateWithIdentifier::candidate);

        assertThat(fetchedCandidate.get(),
                   is(equalTo(expectedCandidate)));
    }

    private CandidateEvaluatedMessage createEvaluatedCandidateDto(UUID identifier,
                                                                  List<CandidateDetails.Creator> creators,
                                                                  String instanceType, Level randomLevel,
                                                                  PublicationDate publicationDate) {
        return CandidateEvaluatedMessage.builder()
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