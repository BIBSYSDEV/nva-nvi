package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record NviCandidateIndexDocument(@JsonProperty(CONTEXT) URI context,
                                        String identifier,
                                        String year,
                                        String type,
                                        PublicationDetails publicationDetails,
                                        List<Affiliation> affiliations) {

    private static final String CONTEXT = "@context";


    @JacocoGenerated
    public static class Builder {

        private URI context;
        private String identifier;
        private String year;
        private String type;
        private PublicationDetails publicationDetails;
        private List<Affiliation> affiliations;

        public Builder withContext(URI context) {
            this.context = context;
            return this;
        }

        public Builder withIdentifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder withYear(String year) {
            this.year = year;
            return this;
        }

        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        public Builder withPublicationDetails(PublicationDetails publicationDetails) {
            this.publicationDetails = publicationDetails;
            return this;
        }

        public Builder withAffiliations(List<Affiliation> affiliations) {
            this.affiliations = affiliations;
            return this;
        }

        public NviCandidateIndexDocument build() {
            return new NviCandidateIndexDocument(context, identifier, year, type, publicationDetails, affiliations);
        }
    }
}
