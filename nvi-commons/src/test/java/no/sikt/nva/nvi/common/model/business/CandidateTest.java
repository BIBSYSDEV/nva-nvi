package no.sikt.nva.nvi.common.model.business;

import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static org.junit.jupiter.api.Assertions.*;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CandidateTest {

    @Test
    void shouldNotLooseDataWhenTransformingToDbModelAndBackWithRandomData() {
        var originalCandidate = createRandomCandidate();
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

    private Candidate createRandomCandidate() {
        return new Candidate.Builder()
                   .withPublicationId(randomUri())
                   .withPublicationBucketUri(randomUri())
                   .withIsApplicable(randomBoolean())
                   .withInstanceType(randomString())
                   .withLevel(randomElement(Level.values()))
                   .withPublicationDate(new PublicationDate(randomString(), randomString(), randomString()))
                   .withIsInternationalCollaboration(randomBoolean())
                   .withCreatorCount(randomInteger())
                   .withCreators(List.of(new Creator(randomUri(), List.of(randomUri()))))
                   .withApprovalStatuses(List.of(randomApprovalStatus()))
                   .withNotes(List.of(new Note(randomUsername(),randomString(), Instant.EPOCH)))
                   .build();
    }

    private ApprovalStatus randomApprovalStatus() {
        return new ApprovalStatus(randomUri(), randomElement(Status.values()), randomUsername(), Instant.EPOCH);
    }

    private Username randomUsername() {
        return new Username(randomString());
    }
}