package no.sikt.nva.nvi.common.validator;

import java.net.URI;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;

public final class UpdateNviCandidateStatusValidator {

    private UpdateNviCandidateStatusValidator() {
    }

    /**
     * This validates the update status request for a given candidate, checking if this institution can set the new
     * status
     *
     * This requires retrieving metadata about each creator's organization, which is not persisted in the database yet.
     * The business rules here should also probably be moved somewhere else and consolidated with other business rules.
     * Once we persist the necessary metadata, we can move this logic to the domain model or service layer.
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
