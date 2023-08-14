package handlers;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Status;

public class TestUtils {

    public static Candidate generatePendingCandidate(URI publicationId,
                                                     List<URI> approvalAffiliations) {
        return new Candidate.Builder()
                   .withPublicationId(publicationId)
                   .withApprovalStatuses(createInstitutionApprovalStatuses(approvalAffiliations))
                   .withInstanceType(randomString())
                   .build();
    }

    private static List<ApprovalStatus> createInstitutionApprovalStatuses(List<URI> institutionApprovals) {
        return institutionApprovals.stream()
                   .map(uri -> new ApprovalStatus.Builder()
                                   .withStatus(Status.PENDING)
                                   .withInstitutionId(uri)
                                   .build()).toList();
    }
}
