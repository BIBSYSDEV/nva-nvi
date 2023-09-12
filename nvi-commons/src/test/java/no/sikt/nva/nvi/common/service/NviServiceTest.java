package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomPublicationDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbCreator;
import no.sikt.nva.nvi.common.db.model.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbLevel;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbStatus;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomString();
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var candidate = createExpectedCandidate(identifier, verifiedCreators, instanceType,
                                                randomLevel, publicationDate, institutionPoints, true);

        var createdCandidate = nviService.upsertCandidate(candidate).get();
        var createdCandidateId = createdCandidate.identifier();

        var fetchedCandidate = nviService.findById(createdCandidateId).get().candidate();

        assertThat(fetchedCandidate, is(equalTo(candidate)));
    }

    @Test
    void shouldUpdateExistingCandidateWhenUpsertIsCalledAndTheCandidateExists() {
        var bucketIdentifier = UUID.randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomString();
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());

        var expectedCandidate = createExpectedCandidate(bucketIdentifier, verifiedCreators, instanceType,
                                                        randomLevel, publicationDate,
                                                        institutionPoints, true);

        var newInstanceType = randomString();
        var updatedCandidate = createExpectedCandidate(bucketIdentifier, verifiedCreators, newInstanceType,
                                                       randomLevel, publicationDate,
                                                       institutionPoints, true);

        var originalUpserted = nviService.upsertCandidate(expectedCandidate).get();
        var updatedUpserted = nviService.upsertCandidate(updatedCandidate).get();
        assertThat(updatedUpserted, is(not(equalTo(originalUpserted))));

        var createdCandidateId = originalUpserted.identifier();

        var fetchedCandidate = nviService.findById(createdCandidateId).get().candidate();

        assertThat(fetchedCandidate, is(equalTo(updatedCandidate)));
    }

    @Test
    void shouldCreateAndFetchPublicationByPublicationId() {
        var identifier = UUID.randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomString();
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType,
                                                        randomLevel, publicationDate,
                                                        institutionPoints, true);
        nviService.upsertCandidate(expectedCandidate).get().identifier();

        var fetchedCandidate = nviService.findByPublicationId(generatePublicationId(identifier)).get().candidate();

        assertThat(fetchedCandidate, is(equalTo(expectedCandidate)));
    }

    @Test
    void shouldCreateUniquenessIdentifierWhenCreatingCandidate() {
        var identifier = UUID.randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomString();
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType,
                                                        randomLevel, publicationDate,
                                                        institutionPoints, true);
        nviService.upsertCandidate(expectedCandidate).get().identifier();

        var items = scanDB().items().size();

        assertThat(items, is(equalTo(3)));
    }

    @Test
    void shouldCreateCandidateWithPendingInstitutionApprovals() {
        var identifier = UUID.randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomString();
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType,
                                                        randomLevel, publicationDate,
                                                        institutionPoints, true);

        nviService.upsertCandidate(expectedCandidate);

        var fetchedCandidate = nviCandidateRepository.findByPublicationId(generatePublicationId(identifier)).map(
            Candidate::candidate);

        assertThat(fetchedCandidate.get(), is(equalTo(expectedCandidate)));
    }

    @Test
    void shouldNotUpsertCandidateWhenNotNviCandidate() {
        var identifier = UUID.randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomString();
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType,
                                                        randomLevel, publicationDate,
                                                        institutionPoints, false);

        var candidate = nviService.upsertCandidate(expectedCandidate);

        assertThat(candidate, is(Optional.empty()));
    }

    @Test
    void nviServiceShouldHandleDatesWithoutDayOrMonthValues() {
        var identifier = UUID.randomUUID();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(randomUri())),
                                       new DbCreator(randomUri(), List.of()));
        var instanceType = randomString();
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = new DbPublicationDate(null, null, "2022");
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType,
                                                        randomLevel, publicationDate,
                                                        Map.of(), true);

        assertDoesNotThrow(() -> nviService.upsertCandidate(expectedCandidate));
    }

    //TODO: Change test when nviService is implemented
    @Test
    void shouldCreateNviPeriod() {
        var period = createPeriod("2050");
        nviService.createPeriod(period);
        assertThat(nviService.getPeriod(period.publishingYear()), is(equalTo(period)));
    }

    @Test
    void shouldUpdateNviPeriod() {
        var originalPeriod = createPeriod("2014");
        nviService.createPeriod(originalPeriod);
        nviService.updatePeriod(originalPeriod.copy().reportingDate(new Date(2060, 03, 25).toInstant()).build());
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

    @Test
    void shouldReturnIllegalArgumentWhenReportingDateIsBeforeNow() {
        var period = DbNviPeriod.builder().reportingDate(Instant.MIN)
                         .publishingYear("2023")
                         .createdBy(DbUsername.builder().value("me").build())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentWhenPublishingYearIsNotAValidYear() {
        var period = DbNviPeriod.builder().reportingDate(Instant.MIN)
                         .publishingYear("now!")
                         .createdBy(DbUsername.builder().value("me").build())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"APPROVED", "REJECTED"})
    void shouldUpsertApproval2(DbStatus status) {
        var identifier = UUID.randomUUID();
        var institutionUri = randomUri();
        var fullCandidate = nviCandidateRepository.create(createDbCandidate(identifier, institutionUri),
                                                          List.of(createDbApprobalStatus(institutionUri)));

        var response = nviService.updateApprovalStatus(fullCandidate.identifier(),
                                                       createApprovalStatus(status, institutionUri));

        assertThat(response.approvalStatuses().get(0).status(), is(equalTo(status)));
    }

    @Test
    void shouldResetIfStatusSetToPending() {

        var identifier = UUID.randomUUID();
        var institutionUri = randomUri();
        var fullCandidate = nviCandidateRepository.create(createDbCandidate(identifier, institutionUri),
                                                          List.of(createDbApprobalStatus(institutionUri)));

        var response = nviService.updateApprovalStatus(fullCandidate.identifier(),
                                                       createApprovalStatus(DbStatus.APPROVED, institutionUri));
        response = nviService.updateApprovalStatus(fullCandidate.identifier(),
                                                   createApprovalStatus(DbStatus.PENDING, institutionUri));

        assertThat(response.approvalStatuses().get(0).status(), is(equalTo(DbStatus.PENDING)));
    }

    @Test
    void shouldUpdateCandidate() {
        var identifier = UUID.randomUUID();
        var institutionUri = randomUri();
        var candidateData = createDbCandidate(identifier, institutionUri);
        List<DbApprovalStatus> dbApprobalStatus = List.of(createDbApprobalStatus(institutionUri));
        var fullCandidate = nviCandidateRepository.create(candidateData,
                                                          dbApprobalStatus);
        var updatedCandidate = createDbCandidate(identifier, institutionUri);
        nviCandidateRepository.update(fullCandidate.identifier(), updatedCandidate,
                                      fullCandidate.approvalStatuses());
        var candidate1 = nviCandidateRepository.findById(fullCandidate.identifier());
        assertThat(candidate1.get().candidate(), is(not(fullCandidate.candidate())));
    }

    private static DbNviPeriod createPeriod(String publishingYear) {
        return DbNviPeriod.builder()
                   .reportingDate(new Date(2050, 03, 25).toInstant())
                   .publishingYear(publishingYear)
                   .createdBy(randomUsername())
                   .build();
    }

    private static DbApprovalStatus createDbApprobalStatus(URI institutionUri) {
        return DbApprovalStatus.builder()
                   .institutionId(institutionUri)
                   .status(DbStatus.APPROVED)
                   .finalizedBy(new DbUsername("metoo"))
                   .finalizedDate(Instant.now())
                   .build();
    }

    private static DbCandidate createDbCandidate(UUID identifier, URI institutionUri) {
        return DbCandidate.builder()
                   .publicationBucketUri(generateS3BucketUri(identifier))
                   .publicationId(generatePublicationId(identifier))
                   .creators(List.of(new DbCreator(randomUri(),
                                                   List.of(institutionUri))))
                   .instanceType(randomString())
                   .level(randomElement(DbLevel.values()))
                   .applicable(true)
                   .publicationDate(new DbPublicationDate(randomString(), randomString(),
                                                          randomString()))
                   .points(List.of(new DbInstitutionPoints(institutionUri, new BigDecimal("1.2"))))
                   .build();
    }

    private static DbApprovalStatus createApprovalStatus(DbStatus status, URI institutionUri) {
        return DbApprovalStatus.builder()
                   .institutionId(institutionUri)
                   .status(status)
                   .finalizedBy(new DbUsername(randomString()))
                   .finalizedDate(Instant.now())
                   .build();
    }

    private static DbUsername randomUsername() {
        return new DbUsername(randomString());
    }

    private static List<DbInstitutionPoints> mapToInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
        return institutionPoints.entrySet()
                   .stream()
                   .map(entry -> new DbInstitutionPoints(entry.getKey(), entry.getValue()))
                   .toList();
    }

    private DbCandidate createExpectedCandidate(UUID identifier, List<DbCreator> creators,
                                                String instanceType,
                                                DbLevel level, DbPublicationDate publicationDate,
                                                Map<URI, BigDecimal> institutionPoints, boolean applicable) {
        return DbCandidate.builder()
                   .publicationBucketUri(generateS3BucketUri(identifier))
                   .publicationId(generatePublicationId(identifier))
                   .creators(creators)
                   .instanceType(instanceType)
                   .level(level)
                   .applicable(applicable)
                   .publicationDate(publicationDate)
                   .points(mapToInstitutionPoints(institutionPoints))
                   .build();
    }
}