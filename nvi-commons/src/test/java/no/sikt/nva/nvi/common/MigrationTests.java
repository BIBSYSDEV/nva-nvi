package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createNoteRequest;
import static no.sikt.nva.nvi.test.TestUtils.createPeriod;
import static no.sikt.nva.nvi.test.TestUtils.createUpdateStatusRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.net.URI;
import java.util.Map.Entry;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MigrationTests extends LocalDynamoTest {

    public static final int DEFAULT_PAGE_SIZE = 700;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private NviService nviService;

    @BeforeEach
    public void setUp() {
        var localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = new PeriodRepository(localDynamo);
        nviService = new NviService(periodRepository, candidateRepository);
    }

    @Test
    void shouldWriteCandidateWithNotesAndApprovalsAsIsWhenMigrating() {
        periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
        nviService = new NviService(periodRepository, candidateRepository);
        var candidate = setupCandidateWithApprovalAndNotes();
        nviService.migrateAndUpdateVersion(DEFAULT_PAGE_SIZE, null);
        var migratedCandidate = Candidate.fetch(candidate::getIdentifier, candidateRepository,
                                                periodRepository);
        assertEquals(candidate, migratedCandidate);
    }

    @Test
    void shouldWriteBackPeriodAsIsWhenMigrating() {
        var period = nviService.createPeriod(createPeriod(String.valueOf(CURRENT_YEAR)));
        nviService.migrateAndUpdateVersion(DEFAULT_PAGE_SIZE, null);
        var migratedPeriod = nviService.getPeriod(period.publishingYear());
        assertEquals(period, migratedPeriod);
    }

    private static URI getInstitutionId(Candidate candidate) {
        return candidate.getApprovals().entrySet().stream().findFirst().map(Entry::getKey).orElse(null);
    }

    private Candidate setupCandidateWithApprovalAndNotes() {
        var candidate = Candidate.upsert(createUpsertCandidateRequest(CURRENT_YEAR), candidateRepository,
                                         periodRepository)
                            .orElseThrow()
                            .createNote(createNoteRequest(randomString(), randomString()));

        return candidate.updateApproval(createUpdateStatusRequest(ApprovalStatus.REJECTED,
                                                                  getInstitutionId(candidate),
                                                                  randomString()));
    }
}
