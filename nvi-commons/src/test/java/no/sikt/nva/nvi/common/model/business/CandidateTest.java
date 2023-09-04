package no.sikt.nva.nvi.common.model.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.Test;

class CandidateTest {

    @Test
    public void shouldConvertBackAndForthBetweeenDbModelWithoutLoosingData() {
        var candidate = TestUtils.randomCandidate();
        var db = candidate.toDynamoDb();
        var candidateFromDb = Candidate.builder().build().fromDynamoDb(db, Candidate.class);
        assertEquals(candidate, candidateFromDb);
    }

}