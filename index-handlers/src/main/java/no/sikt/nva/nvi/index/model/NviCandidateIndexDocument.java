package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeName("NviCandidate")
public record NviCandidateIndexDocument(@JsonProperty(CONTEXT) URI context,
                                        String identifier,
                                        PublicationDetails publicationDetails,
                                        List<Approval> Approvals) {

    private static final String CONTEXT = "@context";

    @JacocoGenerated
    public static class Builder {

        private URI context;
        private String identifier;
        private PublicationDetails publicationDetails;
        private List<Approval> affiliations;

        public Builder withContext(URI context) {
            this.context = context;
            return this;
        }

        public Builder withIdentifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder withPublicationDetails(PublicationDetails publicationDetails) {
            this.publicationDetails = publicationDetails;
            return this;
        }

        public Builder withApprovals(List<Approval> approvals) {
            this.affiliations = approvals;
            return this;
        }

        public NviCandidateIndexDocument build() {
            return new NviCandidateIndexDocument(context, identifier, publicationDetails, affiliations);
        }
    }
}
