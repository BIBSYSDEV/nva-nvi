package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator;
import no.unit.nva.auth.uriretriever.UriRetriever;

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
                                        ApprovalStatus globalApprovalStatus,
                                        int creatorShareCount,
                                        BigDecimal internationalCollaborationFactor,
                                        String reportedPeriod,
                                        String modifiedDate) {

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
        private ApprovalStatus globalApprovalStatus;
        private int creatorShareCount;
        private BigDecimal internationalCollaborationFactor;
        private String reportedPeriod;
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

        public Builder withGlobalApprovalStatus(ApprovalStatus globalApprovalStatus) {
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

        public Builder withReportedPeriod(String reportedPeriod) {
            this.reportedPeriod = reportedPeriod;
            return this;
        }

        public Builder withModifiedDate(String modifiedDate) {
            this.modifiedDate = modifiedDate;
            return this;
        }

        public NviCandidateIndexDocument build() {
            return new NviCandidateIndexDocument(context, id, isApplicable, TYPE, identifier, publicationDetails,
                                                 approvals, numberOfApprovals, points,
                                                 publicationTypeChannelLevelPoints, globalApprovalStatus,
                                                 creatorShareCount, internationalCollaborationFactor, reportedPeriod,
                                                 modifiedDate);
        }
    }
}
