package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.NviService;
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
    void shouldWriteBackEntryAsIsWhenMigrating() {
        var candidate = Candidate.fromRequest(createUpsertCandidateRequest(), candidateRepository, periodRepository)
                            .orElseThrow();
        nviService.refresh(DEFAULT_PAGE_SIZE, null);
        var migratedCandidate = Candidate.fromRequest(candidate::getIdentifier, candidateRepository,
                                                      periodRepository);
        assertEquals(candidate, migratedCandidate);
    }
}
