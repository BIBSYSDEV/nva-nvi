package no.sikt.nva.nvi.common;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createNoteRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpdateStatusRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.net.URI;
import java.util.Map.Entry;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.BatchScanUtil;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MigrationTests extends LocalDynamoTest {

    public static final int DEFAULT_PAGE_SIZE = 700;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private BatchScanUtil batchScanUtil;

    @BeforeEach
    public void setUp() {
        var localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = new PeriodRepository(localDynamo);
        batchScanUtil = new BatchScanUtil(candidateRepository);
    }

    @Test
    void shouldWriteCandidateWithNotesAndApprovalsAsIsWhenMigrating() {
        periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
        batchScanUtil = new BatchScanUtil(candidateRepository);
        var candidate = setupCandidateWithApprovalAndNotes();
        batchScanUtil.migrateAndUpdateVersion(DEFAULT_PAGE_SIZE, null, emptyList());
        var migratedCandidate = Candidate.fetch(candidate::getIdentifier, candidateRepository,
                                                periodRepository);
        assertEquals(candidate, migratedCandidate);
    }

    @Test
    void shouldSetPeriodYearIfMissingWhenMigrating() {
        var dbCandidate = randomCandidate();
        var existingDao = candidateRepository.create(dbCandidate, emptyList(), null);
        batchScanUtil.migrateAndUpdateVersion(DEFAULT_PAGE_SIZE, null, emptyList());
        var migratedCandidate = candidateRepository.findCandidateById(existingDao.identifier()).orElseThrow();
        assertNotNull(migratedCandidate.getPeriodYear());
        assertEquals(dbCandidate.publicationDate().year(), migratedCandidate.getPeriodYear());
    }

    @Test
    void shouldNotMigratePeriodYearCandidateIsNotApplicable() {
        var dbCandidate = randomCandidateBuilder(false).build();
        var existingDao = candidateRepository.create(dbCandidate, emptyList(), null);
        batchScanUtil.migrateAndUpdateVersion(DEFAULT_PAGE_SIZE, null, emptyList());
        var migratedCandidate = candidateRepository.findCandidateById(existingDao.identifier()).orElseThrow();
        assertNull(migratedCandidate.getPeriodYear());
    }

    private static URI getInstitutionId(Candidate candidate) {
        return candidate.getApprovals().entrySet().stream().findFirst().map(Entry::getKey).orElse(null);
    }

    private Candidate setupCandidateWithApprovalAndNotes() {
        var candidate = TestUtils.randomApplicableCandidate(candidateRepository, periodRepository)
                            .createNote(createNoteRequest(randomString(), randomString()), candidateRepository);

        return candidate.updateApproval(createUpdateStatusRequest(ApprovalStatus.REJECTED,
                                                                  getInstitutionId(candidate),
                                                                  randomString()));
    }
}
