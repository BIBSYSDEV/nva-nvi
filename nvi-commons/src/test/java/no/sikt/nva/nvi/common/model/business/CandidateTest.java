package no.sikt.nva.nvi.common.model.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.Test;

class CandidateTest {

    @Test
    public void shouldConvertBackAndForthBetweeenDbModelWithoutLoosingData() {
        var candidate = TestUtils.randomCandidate();
        var db = candidate.toDynamoDb();
        var candidateFromDb = DbCandidate.builder().build().fromDynamoDb(db, DbCandidate.class);
        assertEquals(candidate, candidateFromDb);
    }

}