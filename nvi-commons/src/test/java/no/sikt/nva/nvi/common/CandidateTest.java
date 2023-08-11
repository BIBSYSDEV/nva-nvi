package no.sikt.nva.nvi.common;

import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.model.ApprovalStatus;
import no.sikt.nva.nvi.common.model.Candidate;
import no.sikt.nva.nvi.common.model.Institution;
import no.sikt.nva.nvi.common.model.Level;
import no.sikt.nva.nvi.common.model.Note;
import no.sikt.nva.nvi.common.model.Period;
import no.sikt.nva.nvi.common.model.Status;
import no.sikt.nva.nvi.common.model.Username;
import no.sikt.nva.nvi.common.model.VerifiedCreator;
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

    private Candidate randomCandidate() {
        return new Candidate.Builder()
                   .withPublicationId(randomUri())
                   .withApprovalStatuses(randomApprovalStatuses())
                   .withCreatorCount(randomInteger())
                   .withInstanceType(randomString())
                   .withLevel(Level.LEVEL_ONE)
                   .withIsApplicable(true)
                   .withIsInternationalCollaboration(true)
                   .withCreators(randomVerifiedCreators())
                   .withNotes(randomNotes())
                   .withPeriod(randomPeriod())
                   .build();
    }

    private List<VerifiedCreator> randomVerifiedCreators() {
        return IntStream.range(1, 20).boxed().map(i -> randomVerifiedCreator()).collect(Collectors.toList());
    }

    private VerifiedCreator randomVerifiedCreator() {
        return new VerifiedCreator(randomUri(), randomInstitutions());
    }

    private List<Institution> randomInstitutions() {
        return IntStream.range(1, 20).boxed().map(i -> randomInstitution()).collect(Collectors.toList());
    }

    private Institution randomInstitution() {
        return new Institution(randomUri());
    }

    private List<ApprovalStatus> randomApprovalStatuses() {
        return IntStream.range(1, 20).boxed().map(i -> randomInstitutionStatus()).collect(Collectors.toList());
    }

    private ApprovalStatus randomInstitutionStatus() {
        return new ApprovalStatus.Builder()
                   .withApproval(Status.APPROVED)
                   .withInstitution(randomInstitution())
                   .withFinalizedBy(randomUsername())
                   .withFinalizedDate(Instant.now())
                   .build();
    }

    private Period randomPeriod() {
        return new Period.Builder().withClosed(randomInstant()).withYear(randomLocalDate().getYear()).build();
    }

    private List<Note> randomNotes() {
        return IntStream.range(1, 20).boxed().map(i -> randomNote()).collect(Collectors.toList());
    }

    private Note randomNote() {
        return new Note.Builder()
                   .withCreatedDate(Instant.now())
                   .withText(randomString())
                   .withUser(randomUsername())
                   .build();
    }

    private Username randomUsername() {
        return new Username(randomString());
    }
}
