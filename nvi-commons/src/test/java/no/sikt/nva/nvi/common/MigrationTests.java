package no.sikt.nva.nvi.common;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getEventBasedBatchScanHandlerEnvironment;
import static no.sikt.nva.nvi.common.RequestFixtures.createNoteRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpdateStatusRequest;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidate;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.common.model.UserInstanceFixtures.createCuratorUserInstance;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.DbCreatorTypeListConverter;
import no.sikt.nva.nvi.common.model.CandidateFixtures;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.BatchScanUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class MigrationTests {

  public static final int DEFAULT_PAGE_SIZE = 700;
  private CandidateRepository candidateRepository;
  private PeriodRepository periodRepository;
  private BatchScanUtil batchScanUtil;

  @BeforeEach
  void setUp() {
    var scenario = new TestScenario();
    candidateRepository = scenario.getCandidateRepository();
    periodRepository = scenario.getPeriodRepository();
    batchScanUtil =
        new BatchScanUtil(
            candidateRepository,
            scenario.getS3StorageReaderForExpandedResourcesBucket(),
            new FakeSqsClient(),
            getEventBasedBatchScanHandlerEnvironment());
  }

  @Test
  void shouldWriteCandidateWithNotesAndApprovalsAsIsWhenMigrating() {
    periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
    var candidate = setupCandidateWithApprovalAndNotes();
    batchScanUtil.migrateAndUpdateVersion(DEFAULT_PAGE_SIZE, null, emptyList());
    var migratedCandidate =
        Candidate.fetch(candidate::getIdentifier, candidateRepository, periodRepository);
    assertThat(migratedCandidate)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(candidate);
  }

  @Test
  void shouldSetPeriodYearIfMissingWhenMigrating() {
    var dbCandidate = randomCandidate();
    var existingDao = candidateRepository.create(dbCandidate, emptyList(), null);
    batchScanUtil.migrateAndUpdateVersion(DEFAULT_PAGE_SIZE, null, emptyList());
    var migratedCandidate =
        candidateRepository.findCandidateById(existingDao.identifier()).orElseThrow();
    assertNotNull(migratedCandidate.getPeriodYear());
    assertEquals(dbCandidate.getPublicationDate().year(), migratedCandidate.getPeriodYear());
  }

  @Test
  void shouldNotMigratePeriodYearCandidateIsNotApplicable() {
    var dbCandidate = randomCandidateBuilder(false).build();
    var existingDao = candidateRepository.create(dbCandidate, emptyList(), null);
    batchScanUtil.migrateAndUpdateVersion(DEFAULT_PAGE_SIZE, null, emptyList());
    var migratedCandidate =
        candidateRepository.findCandidateById(existingDao.identifier()).orElseThrow();
    assertNull(migratedCandidate.getPeriodYear());
  }

  @Test
  void shouldDeserializeCreatorWithoutTypeAsDbCreator() {
    var oldCreatorId = randomUri();
    var oldCreatorAffiliations = List.of(randomUri());
    var oldCreatorObject =
        Map.of(
            "creatorId",
            AttributeValue.builder().s(oldCreatorId.toString()).build(),
            "affiliations",
            AttributeValue.builder()
                .l(
                    oldCreatorAffiliations.stream()
                        .map(URI::toString)
                        .map(AttributeValue::fromS)
                        .toList())
                .build());

    var oldDataAttributeValue =
        AttributeValue.builder().l(AttributeValue.builder().m(oldCreatorObject).build()).build();
    var converter = new DbCreatorTypeListConverter();

    var creator = converter.transformTo(oldDataAttributeValue).getFirst();
    var expectedCreator = new DbCreator(oldCreatorId, null, oldCreatorAffiliations);

    assertInstanceOf(DbCreator.class, creator);
    assertEquals(expectedCreator, creator);
  }

  private static URI getInstitutionId(Candidate candidate) {
    return candidate.getApprovals().entrySet().stream().findFirst().map(Entry::getKey).orElse(null);
  }

  private Candidate setupCandidateWithApprovalAndNotes() {
    var candidate =
        CandidateFixtures.setupRandomApplicableCandidate(candidateRepository, periodRepository)
            .createNote(createNoteRequest(randomString(), randomString()), candidateRepository);
    var curatorOrganization = getInstitutionId(candidate);
    var userInstance = createCuratorUserInstance(curatorOrganization);

    return candidate.updateApprovalStatus(
        createUpdateStatusRequest(ApprovalStatus.REJECTED, curatorOrganization, randomString()),
        userInstance);
  }
}
