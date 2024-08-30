package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.paths.UriWrapper;
import org.apache.jena.graph.Graph;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record NviCandidateIndexDocument(@JsonProperty(CONTEXT) URI context,
                                        URI id,
                                        boolean isApplicable,
                                        String type,
                                        UUID identifier,
                                        PublicationDetails publicationDetails,
                                        List<Approval> approvals,
                                        int numberOfApprovals,
                                        BigDecimal points,
                                        BigDecimal publicationTypeChannelLevelPoints,
                                        GlobalApprovalStatus globalApprovalStatus,
                                        int creatorShareCount,
                                        BigDecimal internationalCollaborationFactor,
                                        ReportingPeriod reportingPeriod,
                                        boolean reported,
                                        String createdDate,
                                        String modifiedDate) implements JsonSerializable {

    private static final String CONTEXT = "@context";
    private static final String NVI_CANDIDATE = "NviCandidate";
    private static final String TYPE = NVI_CANDIDATE;

    public static NviCandidateIndexDocument from(JsonNode expandedResource, Candidate candidate,
                                                 UriRetriever uriRetriever) {
        var documentGenerator = new NviCandidateIndexDocumentGenerator(uriRetriever);
        return documentGenerator.generateDocument(expandedResource, candidate);
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonIgnore
    public Approval getApprovalForInstitution(URI institutionId) {
        return approvals.stream()
                   .filter(approval -> approval.institutionId().equals(institutionId))
                   .findAny()
                   .orElseThrow();
    }

    @JsonIgnore
    public BigDecimal getPointsForContributorAffiliation(URI topLevelCristinOrg,
                                                         NviContributor nviContributor,
                                                         NviOrganization affiliation) {
        return getApprovalForInstitution(topLevelCristinOrg).getPointsForAffiliation(nviContributor, affiliation);
    }

    @JsonIgnore
    public String getReportingPeriodYear() {
        return reportingPeriod.year();
    }

    @JsonIgnore
    public String getPublicationIdentifier() {
        return UriWrapper.fromUri(publicationDetails.id()).getLastPathElement();
    }

    @JsonIgnore
    public String getPublicationDateYear() {
        return publicationDetails.publicationDate().year();
    }

    @JsonIgnore
    public String getPublicationInstanceType() {
        return publicationDetails.type();
    }

    @JsonIgnore
    public String getPublicationTitle() {
        return publicationDetails.title();
    }

    @JsonIgnore
    public ApprovalStatus getApprovalStatusForInstitution(URI topLevelCristinOrg) {
        return getApprovalForInstitution(topLevelCristinOrg).approvalStatus();
    }

    @JsonIgnore
    public List<NviContributor> getNviContributors() {
        return publicationDetails.nviContributors();
    }

    public static final class Builder {

        private URI context;
        private URI id;
        private boolean isApplicable;
        private UUID identifier;
        private PublicationDetails publicationDetails;
        private List<Approval> approvals;
        private int numberOfApprovals;
        private BigDecimal points;
        private BigDecimal publicationTypeChannelLevelPoints;
        private GlobalApprovalStatus globalApprovalStatus;
        private int creatorShareCount;
        private BigDecimal internationalCollaborationFactor;
        private ReportingPeriod reportingPeriod;
        private boolean reported;
        private String createdDate;
        private String modifiedDate;

        private Builder() {
        }

        public Builder withContext(URI context) {
            this.context = context;
            return this;
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withIsApplicable(boolean isApplicable) {
            this.isApplicable = isApplicable;
            return this;
        }

        public Builder withIdentifier(UUID identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder withPublicationDetails(PublicationDetails publicationDetails) {
            this.publicationDetails = publicationDetails;
            return this;
        }

        public Builder withApprovals(List<Approval> approvals) {
            this.approvals = approvals;
            return this;
        }

        public Builder withNumberOfApprovals(int numberOfApprovals) {
            this.numberOfApprovals = numberOfApprovals;
            return this;
        }

        public Builder withPoints(BigDecimal points) {
            this.points = points;
            return this;
        }

        public Builder withPublicationTypeChannelLevelPoints(BigDecimal publicationTypeChannelLevelPoints) {
            this.publicationTypeChannelLevelPoints = publicationTypeChannelLevelPoints;
            return this;
        }

        public Builder withGlobalApprovalStatus(GlobalApprovalStatus globalApprovalStatus) {
            this.globalApprovalStatus = globalApprovalStatus;
            return this;
        }

        public Builder withCreatorShareCount(int creatorShareCount) {
            this.creatorShareCount = creatorShareCount;
            return this;
        }

        public Builder withInternationalCollaborationFactor(BigDecimal internationalCollaborationFactor) {
            this.internationalCollaborationFactor = internationalCollaborationFactor;
            return this;
        }

        public Builder withReportingPeriod(ReportingPeriod reportingPeriod) {
            this.reportingPeriod = reportingPeriod;
            return this;
        }

        public Builder withReported(boolean reported) {
            this.reported = reported;
            return this;
        }

        public Builder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate.toString();
            return this;
        }

        public Builder withModifiedDate(Instant modifiedDate) {
            this.modifiedDate = modifiedDate.toString();
            return this;
        }

        public NviCandidateIndexDocument build() {
            return new NviCandidateIndexDocument(context, id, isApplicable, TYPE, identifier, publicationDetails,
                                                 approvals, numberOfApprovals, points,
                                                 publicationTypeChannelLevelPoints, globalApprovalStatus,
                                                 creatorShareCount, internationalCollaborationFactor, reportingPeriod,
                                                 reported, createdDate, modifiedDate);
        }
    }
}
