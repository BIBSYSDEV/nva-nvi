package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public final class NviCandidateIndexDocument {

    private static final String IDENTIFIER = "identifier";
    private static final String YEAR = "year";
    private static final String TYPE = "type";
    private static final String PUBLICATION = "publication";
    private static final String AFFILIATIONS = "affiliations";
    private static final String CONTEXT = "context";
    @JsonProperty(CONTEXT)
    private final URI context;
    @JsonProperty(IDENTIFIER)
    private final String identifier;
    @JsonProperty(YEAR)
    private final String year;
    @JsonProperty(TYPE)
    private final String type;
    @JsonProperty(PUBLICATION)
    private final PublicationDetails publicationDetails;
    @JsonProperty(AFFILIATIONS)
    private final List<Affiliation> affiliations;

    public NviCandidateIndexDocument(
        @JsonProperty(CONTEXT) URI context,
        @JsonProperty(IDENTIFIER) String identifier,
        @JsonProperty(YEAR) String year,
        @JsonProperty(TYPE) String type,
        @JsonProperty(PUBLICATION) PublicationDetails publicationDetails,
        @JsonProperty(AFFILIATIONS) List<Affiliation> affiliations) {
        this.context = context;
        this.identifier = identifier;
        this.year = year;
        this.type = type;
        this.publicationDetails = publicationDetails;
        this.affiliations = affiliations;
    }

    public String getIdentifier() {
        return identifier;
    }

    public PublicationDetails getPublication() {
        return publicationDetails;
    }

    public List<Affiliation> getAffiliations() {
        return affiliations;
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (NviCandidateIndexDocument) obj;
        return Objects.equals(this.context, that.context)
               && Objects.equals(this.identifier, that.identifier)
               && Objects.equals(this.year, that.year)
               && Objects.equals(this.type, that.type)
               && Objects.equals(this.publicationDetails, that.publicationDetails)
               && Objects.equals(this.affiliations, that.affiliations);
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(context, identifier, year, type, publicationDetails, affiliations);
    }

    @Override
    @JacocoGenerated
    public String toString() {
        return "NviCandidateIndexDocument["
               + "context=" + context + ", "
               + "identifier=" + identifier + ", "
               + "year=" + year + ", "
               + "type=" + type + ", "
               + "publication=" + publicationDetails + ", "
               + "affiliations=" + affiliations + ']';
    }
}
