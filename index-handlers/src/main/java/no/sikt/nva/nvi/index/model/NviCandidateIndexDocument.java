package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeName("NviCandidate")
public record NviCandidateIndexDocument(@JsonProperty(CONTEXT) URI context,
                                        UUID identifier,
                                        PublicationDetails publicationDetails,
                                        List<Approval> approvals,
                                        int numberOfApprovals,
                                        BigDecimal points) {

    private static final String CONTEXT = "@context";

    public static NviCandidateIndexDocument from(JsonNode expandedResource, Candidate candidate,
                                                 UriRetriever uriRetriever) {
        var documentGenerator = new NviCandidateIndexDocumentGenerator(uriRetriever);
        return documentGenerator.generateDocument(expandedResource, candidate);
    }

    public static Builder builder() {
        return new Builder();
    }

    @JacocoGenerated
    public static class Builder {

        private URI context;
        private UUID identifier;
        private PublicationDetails publicationDetails;
        private List<Approval> approvals;
        private int numberOfApprovals;
        private BigDecimal points;

        public Builder withContext(URI context) {
            this.context = context;
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

        public NviCandidateIndexDocument build() {
            return new NviCandidateIndexDocument(context, identifier, publicationDetails,
                                                 approvals, numberOfApprovals, points);
        }
    }
}
