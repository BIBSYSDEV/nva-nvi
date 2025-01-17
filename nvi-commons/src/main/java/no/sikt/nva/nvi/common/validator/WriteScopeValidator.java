package no.sikt.nva.nvi.common.validator;

import java.net.URI;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.unit.nva.auth.uriretriever.UriRetriever;

public class WriteScopeValidator {

    private final OrganizationRetriever organizationRetriever;

    public WriteScopeValidator(UriRetriever uriRetriever) {
        this.organizationRetriever = new OrganizationRetriever(uriRetriever);
    }

    public Candidate validateUpdateStatusRequest(Candidate candidate, UpdateStatusRequest updateRequest) {
        var hasUnverifiedCreator = hasUnverifiedCreator(candidate, updateRequest.institutionId());
        var newState = updateRequest.approvalStatus();
        if (hasUnverifiedCreator && !newState.equals(ApprovalStatus.PENDING)) {
            throw new IllegalStateException("Cannot finalize status for institution with unverified creator");
        }
        return candidate;
    }

    /**
     * This method validates the update status request for a given candidate, and throws an exception if this
     * institution cannot set the status to the requested state. This logic should ideally be moved to the domain model
     * or service layer, but is currently placed here because we need to check top-level organizations of unverified
     * creators and this information is not persisted.
     * <p>
     * TODO: Persist full organization hierarchy for creators and move this validation.
     */
    private boolean hasUnverifiedCreator(Candidate candidate, URI customerId) {
        var customerOrganization = organizationRetriever
                                       .fetchOrganization(customerId)
                                       .getTopLevelOrg();
        var unverifiedCreators = candidate
                                     .getPublicationDetails()
                                     .getUnverifiedCreators();
        return unverifiedCreators
                   .stream()
                   .flatMap(this::getTopLevelAffiliations)
                   .filter(org -> org
                                      .id()
                                      .equals(customerOrganization.id()))
                   .findAny()
                   .isPresent();
    }

    private Stream<Organization> getTopLevelAffiliations(NviCreatorDto contributor) {
        return contributor
                   .affiliations()
                   .stream()
                   .distinct()
                   .map(organizationRetriever::fetchOrganization)
                   .map(Organization::getTopLevelOrg);
    }
}
