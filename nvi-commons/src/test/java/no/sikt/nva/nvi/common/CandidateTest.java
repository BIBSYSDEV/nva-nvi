package no.sikt.nva.nvi.common;

import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.model.ApprovalStatus;
import no.sikt.nva.nvi.common.model.Candidate;
import no.sikt.nva.nvi.common.model.Creator;
import no.sikt.nva.nvi.common.model.Foundation;
import no.sikt.nva.nvi.common.model.InstitutionStatus;
import no.sikt.nva.nvi.common.model.Level;
import no.sikt.nva.nvi.common.model.Note;
import no.sikt.nva.nvi.common.model.NviPeriod;
import no.sikt.nva.nvi.common.model.Status;
import no.sikt.nva.nvi.common.model.Username;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Test;

public class CandidateTest {

    @Test
    void shouldMakeRoundTripWithoutLossOfInformation() throws JsonProcessingException {
        var candidate = randomCandidate();
        var json = JsonUtils.dtoObjectMapper.writeValueAsString(candidate);
        var reconstructedCandidate = JsonUtils.dtoObjectMapper.readValue(json, Candidate.class);
        assertThat(reconstructedCandidate, is(equalTo(candidate)));
    }

    @Test
    void shouldCopyCandidate() {
        var candidate = randomCandidate();
        var copy = candidate.copy().build();
        assertThat(copy, is(equalTo(candidate)));
    }

    @Test
    void shouldBeAbleToModifyCandidate() {
        var candidate = randomCandidate();
        var copy = candidate.copy()
                       .withNotes(List.of())
                       .build();
        assertThat(copy, is(not(equalTo(candidate))));
    }

    private Candidate randomCandidate() {
        return new Candidate.Builder()
                   .withPublicationId(randomUri())
                   .withFoundation(randomFoundation())
                   .withNotes(randomNotes())
                   .withPeriod(randomPeriod())
                   .withInstitutionStatuses(randomInstitutionStatuses())
                   .build();
    }

    private List<InstitutionStatus> randomInstitutionStatuses() {
        return IntStream.range(1, 20)
                   .boxed()
                   .map(i -> randomInstitutionStatus())
                   .collect(Collectors.toList());
    }

    private InstitutionStatus randomInstitutionStatus() {
        return new InstitutionStatus.Builder()
                   .withApprovalStatus(randomApprovalStatus())
                   .withInstitutionId(randomUsername())
                   .build();
    }

    private ApprovalStatus randomApprovalStatus() {
        return new ApprovalStatus.Builder()
                   .withApprovedBy(randomUri())
                   .withStatus(Status.APPROVED)
                   .withApprovalDate(randomInstant())
                   .build();
    }

    private NviPeriod randomPeriod() {
        return new NviPeriod.Builder()
                   .withEnd(randomInstant())
                   .withStart(randomInstant())
                   .withYear(randomLocalDate().getYear())
                   .build();
    }

    private List<Note> randomNotes() {
        return IntStream.range(1, 20)
                   .boxed()
                   .map(i -> randomNote())
                   .collect(Collectors.toList());
    }

    private Note randomNote() {
        return new Note.Builder()
                   .withText(randomString())
                   .withUser(randomUsername())
                   .build();
    }

    private Username randomUsername() {
        return new Username(randomString());
    }

    private Foundation randomFoundation() {
        return new Foundation.Builder()
                   .withCreators(randomCreators())
                   .withInstanceType(randomString())
                   .withInternational(randomBoolean())
                   .withTotalCreator(randomInteger())
                   .withLevel(Level.SOME_LEVEL)
                   .build();
    }

    private List<Creator> randomCreators() {
        return IntStream.range(1, 20)
                   .boxed()
                   .map(i -> new Creator(randomUsername()))
                   .collect(Collectors.toList());
    }
}
