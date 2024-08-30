package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints.CreatorAffiliationPoints;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type")
@JsonTypeName("Approval")
public record Approval(URI institutionId,
                       Map<String, String> labels,
                       ApprovalStatus approvalStatus,
                       InstitutionPoints points,
                       Set<URI> involvedOrganizations,
                       String assignee,
                       GlobalApprovalStatus globalApprovalStatus) {

    public static Builder builder() {
        return new Builder();
    }

    public BigDecimal getPointsForAffiliation(NviContributor nviContributor, NviOrganization affiliation) {
        return points.creatorAffiliationPoints().stream()
                   .filter(isEqual(affiliation))
                   .filter(isNviContributor(nviContributor))
                   .map(CreatorAffiliationPoints::points)
                   .findFirst()
                   .orElseThrow();
    }

    private static Predicate<CreatorAffiliationPoints> isNviContributor(NviContributor nviContributor) {
        return creatorAffiliationPoints -> creatorAffiliationPoints.nviCreator().toString().equals(nviContributor.id());
    }

    private static Predicate<CreatorAffiliationPoints> isEqual(NviOrganization affiliation) {
        return creatorAffiliationPoints -> creatorAffiliationPoints.affiliationId().equals(affiliation.id());
    }

    public static final class Builder {

        private URI institutionId;
        private Map<String, String> labels;
        private ApprovalStatus approvalStatus;
        private InstitutionPoints points;
        private Set<URI> involvedOrganizations;
        private String assignee;
        private GlobalApprovalStatus globalApprovalStatus;

        private Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withLabels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder withApprovalStatus(ApprovalStatus approvalStatus) {
            this.approvalStatus = approvalStatus;
            return this;
        }

        public Builder withPoints(InstitutionPoints points) {
            this.points = points;
            return this;
        }

        public Builder withInvolvedOrganizations(Set<URI> involvedOrganizations) {
            this.involvedOrganizations = involvedOrganizations;
            return this;
        }

        public Builder withAssignee(String assignee) {
            this.assignee = assignee;
            return this;
        }

        public Builder withGlobalApprovalStatus(GlobalApprovalStatus globalApprovalStatus) {
            this.globalApprovalStatus = globalApprovalStatus;
            return this;
        }

        public Approval build() {
            return new Approval(institutionId, labels, approvalStatus, points, involvedOrganizations, assignee,
                                globalApprovalStatus);
        }
    }
}
