package handlers;

import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import handlers.model.InstitutionApprovals;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Institution;
import no.sikt.nva.nvi.common.model.business.PublicationDate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.VerifiedCreator;

public class TestUtils {

    public static Candidate generatePendingCandidate(URI publicationId,
                                                     List<InstitutionApprovals> institutionApprovals) {
        var institutions = createInstitutions(institutionApprovals);
        return new Candidate.Builder()
                   .withPublicationId(publicationId)
                   .withApprovalStatuses(createInstitutionApprovalStatuses(institutionApprovals, institutions))
                   .withCreatorCount(countCreators(institutionApprovals))
                   .withInstanceType(randomString())
                   .withCreators(createCreators(institutionApprovals, institutions))
                   .withPublicationDate(randomPublicationDate())
                   .build();
    }

    private static PublicationDate randomPublicationDate() {
        var randomLocalDate = randomLocalDate();
        return new PublicationDate(String.valueOf(randomLocalDate.getYear()),
                                   String.valueOf(randomLocalDate.getMonthValue()),
                                   String.valueOf(randomLocalDate.getDayOfMonth()));
    }

    private static List<Institution> createInstitutions(List<InstitutionApprovals> institutionApprovals) {
        return institutionApprovals.stream()
                   .map(institutionApproval -> new Institution(URI.create(institutionApproval.institutionId())))
                   .toList();
    }

    private static List<VerifiedCreator> createCreators(List<InstitutionApprovals> institutionApprovals,
                                                        List<Institution> institutions) {
        var verifiedCreatorIds = extractCreatorIds(institutionApprovals);
        return verifiedCreatorIds.stream()
                   .map(creatorId -> new VerifiedCreator(URI.create(creatorId),
                                                         getAffiliationsForCreator(creatorId, institutionApprovals,
                                                                                   institutions)))
                   .toList();
    }

    private static List<Institution> getAffiliationsForCreator(String creatorId,
                                                               List<InstitutionApprovals> institutionApprovals,
                                                               List<Institution> institutions) {
        return institutionApprovals.stream()
                   .filter(institutionApproval -> institutionApproval.verifiedCreators().contains(creatorId))
                   .map(InstitutionApprovals::institutionId)
                   .map(id -> getInstitutionById(institutions, id)).toList();
    }

    private static Institution getInstitutionById(List<Institution> institutions, String id) {
        return institutions.stream()
                   .filter(institution -> institution.id().equals(URI.create(id)))
                   .findFirst()
                   .orElse(null);
    }

    private static List<ApprovalStatus> createInstitutionApprovalStatuses(
        List<InstitutionApprovals> institutionApprovals, List<Institution> institutions) {
        return institutionApprovals.stream()
                   .map(InstitutionApprovals::institutionId)
                   .map(id -> new ApprovalStatus.Builder()
                                  .withStatus(Status.PENDING)
                                  .withInstitution(getInstitutionById(institutions, id))
                                  .build()).toList();
    }

    private static int countCreators(List<InstitutionApprovals> institutionApprovals) {
        return extractCreatorIds(institutionApprovals).size();
    }

    private static Set<String> extractCreatorIds(List<InstitutionApprovals> institutionApprovals) {
        return institutionApprovals.stream()
                   .flatMap(institutionApproval -> institutionApproval.verifiedCreators().stream())
                   .collect(Collectors.toSet());
    }
}
