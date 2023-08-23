package no.sikt.nva.nvi.common.model.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.Test;

public class CandidateTest {

    @Test
    void shouldNotLooseDataWhenTransformingToDbModelAndBackWithRandomData() {
        var originalCandidate = TestUtils.randomCandidate();
        var candidateDb = originalCandidate.toDb();
        var convertedCandidate = Candidate.fromDb(candidateDb);
        assertEquals(originalCandidate, convertedCandidate);
    }

    @Test
    void shouldNotLooseDataWhenTransformingToDbModelAndBackWithNullData() {
        var originalCandidate = new Candidate.Builder().build();
        var candidateDb = originalCandidate.toDb();
        var convertedCandidate = Candidate.fromDb(candidateDb);
        assertEquals(originalCandidate, convertedCandidate);
    }

}