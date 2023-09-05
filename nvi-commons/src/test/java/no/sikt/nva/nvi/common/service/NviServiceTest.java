package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.mapToVerifiedCreators;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.DbApprovalStatus;
import no.sikt.nva.nvi.common.model.business.DbCandidate;
import no.sikt.nva.nvi.common.model.business.InstitutionPoints;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.NviPeriod;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.Username;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.common.model.events.CandidateStatus;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails.Creator;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails.PublicationDate;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class NviServiceTest extends LocalDynamoTest {

    private NviService nviService;

    private NviCandidateRepository nviCandidateRepository;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        nviCandidateRepository = new NviCandidateRepository(localDynamo);
        nviService = new NviService(localDynamo);
    }

    @Test
    void shouldCreateAndFetchPublicationById() {
        var identifier = UUID.randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new Creator(randomUri(), List.of(institutionId)));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var evaluatedCandidateDto = createEvaluatedCandidateDto(identifier, verifiedCreators, instanceType, randomLevel,
                                                                publicationDate, institutionPoints);

        var createdCandidate = nviService.upsertCandidate(evaluatedCandidateDto).get();
        var createdCandidateId = createdCandidate.identifier();

        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate, institutionPoints);
        var fetchedCandidate = nviService.findById(createdCandidateId).get().candidate();

        assertThat(fetchedCandidate, is(equalTo(expectedCandidate)));
    }

    @Test
    void shouldUpdateExistingCandidateWhenUpsertIsCalledAndTheCandidateExists() {
        var bucketIdentifier = UUID.randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new Creator(randomUri(), List.of(institutionId)));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var originalEvaluatedCandidateDto = createEvaluatedCandidateDto(bucketIdentifier, verifiedCreators,
                                                                        instanceType, randomLevel,
                                                                        publicationDate, institutionPoints);

        var newInstanceType = randomString();
        var updatedEvaluatedCandidateDto = createEvaluatedCandidateDto(bucketIdentifier, verifiedCreators,
                                                                       newInstanceType, randomLevel,
                                                                       publicationDate, institutionPoints);

        var originalUpserted = nviService.upsertCandidate(originalEvaluatedCandidateDto).get();
        var updatedUpserted = nviService.upsertCandidate(updatedEvaluatedCandidateDto).get();
        assertThat(updatedUpserted, is(not(equalTo(originalUpserted))));

        var createdCandidateId = originalUpserted.identifier();

        var expectedCandidate = createExpectedCandidate(bucketIdentifier, verifiedCreators, newInstanceType,
                                                        randomLevel, publicationDate, institutionPoints);
        var fetchedCandidate = nviService.findById(createdCandidateId).get().candidate();

        assertThat(fetchedCandidate, is(equalTo(expectedCandidate)));
    }

    @Test
    void shouldCreateAndFetchPublicationByPublicationId() {
        var identifier = UUID.randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new Creator(randomUri(), List.of(institutionId)));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var evaluatedCandidateDto = createEvaluatedCandidateDto(identifier, verifiedCreators, instanceType, randomLevel,
                                                                publicationDate, institutionPoints);

        nviService.upsertCandidate(evaluatedCandidateDto).get().identifier();

        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate, institutionPoints);
        var fetchedCandidate = nviService.findByPublicationId(generatePublicationId(identifier)).get().candidate();

        assertThat(fetchedCandidate, is(equalTo(expectedCandidate)));
    }

    @Test
    void shouldCreateUniquenessIdentifierWhenCreatingCandidate() {
        var identifier = UUID.randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new Creator(randomUri(), List.of(institutionId)));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var evaluatedCandidateDto = createEvaluatedCandidateDto(identifier, verifiedCreators, instanceType, randomLevel,
                                                                publicationDate, institutionPoints);
        nviService.upsertCandidate(evaluatedCandidateDto).get().identifier();

        var items = scanDB().items().size();

        assertThat(items, is(equalTo(3)));
    }

    @Test
    void shouldCreateCandidateWithPendingInstitutionApprovals() {
        var identifier = UUID.randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new Creator(randomUri(), List.of(institutionId)));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var evaluatedCandidateDto = createEvaluatedCandidateDto(identifier, verifiedCreators, instanceType, randomLevel,
                                                                publicationDate, institutionPoints);

        nviService.upsertCandidate(evaluatedCandidateDto);

        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate, institutionPoints);
        var fetchedCandidate = nviCandidateRepository.findByPublicationId(generatePublicationId(identifier)).map(
            CandidateWithIdentifier::candidate);

        assertThat(fetchedCandidate.get(), is(equalTo(expectedCandidate)));
    }

    @Test
    void nviServiceShouldHandleDatesWithoutDayOrMonthValues() {
        var identifier = UUID.randomUUID();
        var verifiedCreators = List.of(new Creator(randomUri(), List.of(randomUri())),
                                       new Creator(randomUri(), List.of()));
        var instanceType = randomString();
        var randomLevel = randomElement(Level.values());
        var publicationDate = new PublicationDate(null, null, "2022");
        var evaluatedCandidateDto =
            createEvaluatedCandidateDto(identifier, verifiedCreators, instanceType, randomLevel, publicationDate,
                                        Map.of());

        assertDoesNotThrow(() -> nviService.upsertCandidate(evaluatedCandidateDto));
    }

    //TODO: Change test when nviService is implemented
    @Test
    void shouldCreateNviPeriod() {
        var period = createPeriod("2014");
        nviService.createPeriod(period);
        assertThat(nviService.getPeriod(period.publishingYear()), is(equalTo(period)));
    }

    @Test
    void shouldThrowExceptionIdPeriodIsNonNumeric() {
        var period = createPeriod("2OI4");
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldUpdateNviPeriod() {
        var originalPeriod = createPeriod("2014");
        nviService.createPeriod(originalPeriod);
        nviService.updatePeriod(originalPeriod.copy().withReportingDate(randomInstant()).build());
        var fetchedPeriod = nviService.getPeriod(originalPeriod.publishingYear());
        assertThat(fetchedPeriod, is(not(equalTo(originalPeriod))));
    }

    @Test
    void shouldReturnBadRequestWhenPublishingYearIsNotAYear() {
        var period = createPeriod(randomString());
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnBadRequestWhenPublishingYearHasInvalidLength() {
        var period = createPeriod("22");
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"APPROVED", "REJECTED"})
    void shouldUpsertApproval2(Status status) {
        var identifier = UUID.randomUUID();
        var institutionUri = randomUri();
        var fullCandidate = nviCandidateRepository.create(createDbCandidate(identifier, institutionUri),
                                                          List.of(createDbApprobalStatus(institutionUri)));

        List<Map<String, AttributeValue>> list = scanDB().items().stream().toList();
        var response = nviService.updateApprovalStatus(fullCandidate.identifier(),
                                                       createApprovalStatus(status, institutionUri));

        assertThat(response.candidate().approvalStatuses().get(0).status(), is(equalTo(status)));
    }

    @Test
    void shouldUpdateCandidate() {
        var identifier = UUID.randomUUID();
        var institutionUri = randomUri();
        var candidateData = createDbCandidate(identifier, institutionUri);
        List<DbApprovalStatus> dbApprobalStatus = List.of(createDbApprobalStatus(institutionUri));
        var fullCandidate = nviCandidateRepository.create(candidateData,
                                                          dbApprobalStatus);
        var updatedCandidateData = createDbCandidate(identifier, institutionUri);
        nviCandidateRepository.update(fullCandidate.identifier(), updatedCandidateData,
                                      fullCandidate.approvalStatuses());
        Candidate candidate1 = nviCandidateRepository.get(identifier);
        assertThat(candidate1.candidate(), is(not(fullCandidate.candidate())));
    }

    private static DbApprovalStatus createDbApprobalStatus(URI institutionUri) {
        return DbApprovalStatus.builder()
                   .institutionId(institutionUri)
                   .status(DbStatus.APPROVED)
                   .finalizedBy(new DbUsername("metoo"))
                   .finalizedDate(Instant.now())
                   .build();
    }

    private static CandidateData createDbCandidate(UUID identifier, URI institutionUri) {
        return CandidateData.builder()
                   .publicationBucketUri(generateS3BucketUri(identifier))
                   .publicationId(generatePublicationId(identifier))
                   .creators(mapToVerifiedCreators(List.of(new Creator(randomUri(),
                                                                       List.of(institutionUri)))))
                   .instanceType(randomString())
                   .level(randomElement(DbLevel.values()))
                   .applicable(true)
                   .publicationDate(new DbPublicationDate(randomString(), randomString(),
                                                          randomString()))
                   .points(List.of(new DbInstitutionPoints(institutionUri, new BigDecimal("1.2"))))
                   .build();
    }

    private static DbApprovalStatus createApprovalStatus(Status status, URI institutionUri) {
        return DbApprovalStatus.builder().withInstitutionId(institutionUri)
                   .withStatus(status)
                   .withFinalizedBy(new Username(randomString()))
                   .withFinalizedDate(Instant.now())
                   .build();
    }

    private static NviPeriod createPeriod(String publishingYear) {
        var start = randomInstant();
        return new NviPeriod.Builder()
                   .withReportingDate(start)
                   .withPublishingYear(publishingYear)
                   .withCreatedBy(randomUsername())
                   .build();
    }

    private static Username randomUsername() {
        return new Username(randomString());
    }

    private static List<InstitutionPoints> mapToInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
        return institutionPoints.entrySet()
                   .stream()
                   .map(entry -> new InstitutionPoints(entry.getKey(), entry.getValue()))
                   .toList();
    }

    private CandidateEvaluatedMessage createEvaluatedCandidateDto(UUID bucketUriIdentifier,
                                                                  List<CandidateDetails.Creator> creators,
                                                                  String instanceType, Level randomLevel,
                                                                  PublicationDate publicationDate,
                                                                  Map<URI, BigDecimal> institutionPoints) {
        return CandidateEvaluatedMessage.builder()
                   .withStatus(CandidateStatus.CANDIDATE)
                   .withPublicationBucketUri(generateS3BucketUri(bucketUriIdentifier))
                   .withInstitutionPoints(institutionPoints)
                   .withCandidateDetails(new CandidateDetails(generatePublicationId(bucketUriIdentifier),
                                                              instanceType,
                                                              randomLevel.getValue(),
                                                              publicationDate,
                                                              creators))
                   .build();
    }

    private DbCandidate createExpectedCandidate(UUID identifier, List<CandidateDetails.Creator> creators,
                                                String instanceType,
                                                Level level, PublicationDate publicationDate,
                                                Map<URI, BigDecimal> institutionPoints) {
        return DbCandidate.builder()
                   .withPublicationBucketUri(generateS3BucketUri(identifier))
                   .withPublicationId(generatePublicationId(identifier))
                   .withCreators(mapToVerifiedCreators(creators))
                   .withInstanceType(instanceType)
                   .withLevel(level)
                   .withIsApplicable(true)
                   .withPublicationDate(toPublicationDate(publicationDate))
                   .withPoints(mapToInstitutionPoints(institutionPoints))
                   .withApprovalStatuses(
                       institutionPoints.keySet()
                           .stream()
                           .map(institutionId -> DbApprovalStatus.builder()
                                                     .withStatus(Status.PENDING)
                                                     .withInstitutionId(institutionId)
                                                     .build()).toList())
                   .build();
    }
}