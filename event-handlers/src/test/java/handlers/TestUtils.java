package handlers;

import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Institution;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.Note;
import no.sikt.nva.nvi.common.model.business.Period;
import no.sikt.nva.nvi.common.model.business.PublicationDate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.Username;
import no.sikt.nva.nvi.common.model.business.VerifiedCreator;

public class TestUtils {

    public static Candidate generatePendingCandidate(URI publicationId, List<URI> institutionApprovals) {
        return new Candidate.Builder()
                   .withPublicationId(publicationId)
                   .withApprovalStatuses(createInstitutionApprovalStatuses(institutionApprovals))
                   .withCreatorCount(randomInteger())
                   .withInstanceType(randomString())
                   .withLevel(Level.LEVEL_ONE)
                   .withIsApplicable(true)
                   .withIsInternationalCollaboration(true)
                   .withCreators(randomVerifiedCreators())
                   .withNotes(randomNotes())
                   .withPeriod(randomPeriod())
                   .withPublicationDate(localDateNowAsPublicationDate())
                   .build();
    }

    private static List<ApprovalStatus> createInstitutionApprovalStatuses(List<URI> institutionApprovals) {
        return institutionApprovals.stream().map(TestUtils::pendingApprovalStatusWithInstitutionId).toList();
    }

    private static PublicationDate localDateNowAsPublicationDate() {
        var now = LocalDate.now();
        return new PublicationDate(String.valueOf(now.getYear()), String.valueOf(now.getMonth()),
                                   String.valueOf(now.getDayOfMonth()));
    }

    private static List<VerifiedCreator> randomVerifiedCreators() {
        return IntStream.range(1, 20).boxed().map(i -> randomVerifiedCreator()).collect(Collectors.toList());
    }

    private static VerifiedCreator randomVerifiedCreator() {
        return new VerifiedCreator(randomUri(), randomInstitutions());
    }

    private static List<Institution> randomInstitutions() {
        return IntStream.range(1, 20).boxed().map(i -> randomInstitution()).collect(Collectors.toList());
    }

    private static Institution randomInstitution() {
        return new Institution(randomUri());
    }
    private static ApprovalStatus pendingApprovalStatusWithInstitutionId(URI id) {
        return new ApprovalStatus.Builder()
                   .withApproval(Status.PENDING)
                   .withInstitution(new Institution(id))
                   .build();
    }

    private static Period randomPeriod() {
        return new Period.Builder().withClosed(randomInstant()).withYear(randomLocalDate().getYear()).build();
    }

    private static List<Note> randomNotes() {
        return IntStream.range(1, 20).boxed().map(i -> randomNote()).collect(Collectors.toList());
    }

    private static Note randomNote() {
        return new Note.Builder()
                   .withCreatedDate(Instant.now())
                   .withText(randomString())
                   .withUser(randomUsername())
                   .build();
    }

    private static Username randomUsername() {
        return new Username(randomString());
    }

}
