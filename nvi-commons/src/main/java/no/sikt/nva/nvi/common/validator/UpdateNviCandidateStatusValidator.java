package no.sikt.nva.nvi.common.validator;

import java.net.URI;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class UpdateNviCandidateStatusValidator {

    private UpdateNviCandidateStatusValidator() {
    }

    /**
     * This validates the update status request for a given candidate, and throws an exception if this institution
     * cannot set the status to the requested state. This logic should ideally be moved to the domain model or service
     * layer, but is currently placed here because we need to check top-level organizations of unverified creators and
     * this information is not persisted.
     * TODO: Persist full organization hierarchy for creators and move this validation.
     */
    public static boolean isValidUpdateStatusRequest(Candidate candidate,
                                                     UpdateStatusRequest updateRequest,
                                                     OrganizationRetriever organizationRetriever) {
        var hasUnverifiedCreator = hasUnverifiedCreator(candidate,
                                                        updateRequest.institutionId(),
                                                        organizationRetriever);
        var newState = updateRequest.approvalStatus();

        return switch (newState) {
            case PENDING -> true;
            case APPROVED, REJECTED -> !hasUnverifiedCreator;
        };
    }

    private static boolean hasUnverifiedCreator(Candidate candidate,
                                                URI customerId,
                                                OrganizationRetriever organizationRetriever) {
        var customerOrganization = organizationRetriever
                                       .fetchOrganization(customerId)
                                       .getTopLevelOrg();
        var unverifiedCreators = candidate
                                     .getPublicationDetails()
                                     .getUnverifiedCreators();
        return unverifiedCreators
                   .stream()
                   .flatMap(contributor -> getTopLevelAffiliations(contributor, organizationRetriever))
                   .anyMatch(org -> org
                                        .id()
                                        .equals(customerOrganization.id()));
    }

    private static Stream<Organization> getTopLevelAffiliations(NviCreatorDto contributor,
                                                                OrganizationRetriever organizationRetriever) {
        return contributor
                   .affiliations()
                   .stream()
                   .distinct()
                   .map(organizationRetriever::fetchOrganization)
                   .map(Organization::getTopLevelOrg);
    }
}
