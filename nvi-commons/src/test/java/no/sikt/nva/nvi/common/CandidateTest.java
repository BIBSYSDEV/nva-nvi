package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
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
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.model.business.DbApprovalStatus;
import no.sikt.nva.nvi.common.model.business.DbCandidate;
import no.sikt.nva.nvi.common.model.business.InstitutionPoints;
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
        var reconstructedCandidate = JsonUtils.dtoObjectMapper.readValue(json, DbCandidate.class);
        assertThat(reconstructedCandidate, is(equalTo(candidate)));
    }

    private DbCandidate randomCandidate() {
        return DbCandidate.builder()
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
                   .withPoints(List.of(new InstitutionPoints(randomUri(), randomBigDecimal())))
                   .build();
    }

    private PublicationDate localDateNowAsPublicationDate() {
        var now = LocalDate.now();
        return new PublicationDate(String.valueOf(now.getYear()), String.valueOf(now.getMonth()),
                                   String.valueOf(now.getDayOfMonth()));
    }

    private List<DbCreator> randomVerifiedCreators() {
        return IntStream.range(1, 20).boxed().map(i -> randomVerifiedCreator()).toList();
    }

    private DbCreator randomVerifiedCreator() {
        return new DbCreator(randomUri(), randomAffiliations());
    }

    private List<URI> randomAffiliations() {
        return IntStream.range(1, 20).boxed().map(i -> randomUri()).toList();
    }

    private List<DbApprovalStatus> randomApprovalStatuses() {
        return IntStream.range(1, 20).boxed().map(i -> randomInstitutionStatus()).toList();
    }

    private DbApprovalStatus randomInstitutionStatus() {
        return DbApprovalStatus.builder()
                   .withStatus(Status.APPROVED)
                   .withInstitutionId(randomUri())
                   .withFinalizedBy(randomUsername())
                   .withFinalizedDate(Instant.now())
                   .build();
    }

    private List<Note> randomNotes() {
        return IntStream.range(1, 20).boxed().map(i -> randomNote()).toList();
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
