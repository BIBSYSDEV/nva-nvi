package no.sikt.nva.nvi.common;

import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Creator;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.Note;
import no.sikt.nva.nvi.common.model.business.PublicationDate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.Username;
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
                   .withPublicationDate(localDateNowAsPublicationDate())
                   .build();
    }

    private PublicationDate localDateNowAsPublicationDate() {
        var now = LocalDate.now();
        return new PublicationDate(String.valueOf(now.getYear()), String.valueOf(now.getMonth()),
                                   String.valueOf(now.getDayOfMonth()));
    }

    private List<Creator> randomVerifiedCreators() {
        return IntStream.range(1, 20).boxed().map(i -> randomVerifiedCreator()).collect(Collectors.toList());
    }

    private Creator randomVerifiedCreator() {
        return new Creator(randomUri(), randomAffiliations());
    }

    private List<URI> randomAffiliations() {
        return IntStream.range(1, 20).boxed().map(i -> randomUri()).collect(Collectors.toList());
    }

    private List<ApprovalStatus> randomApprovalStatuses() {
        return IntStream.range(1, 20).boxed().map(i -> randomInstitutionStatus()).collect(Collectors.toList());
    }

    private ApprovalStatus randomInstitutionStatus() {
        return new ApprovalStatus.Builder()
                   .withStatus(Status.APPROVED)
                   .withInstitutionId(randomUri())
                   .withFinalizedBy(randomUsername())
                   .withFinalizedDate(Instant.now())
                   .build();
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
