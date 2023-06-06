package no.sikt.nva.nvi.create;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public class CreateRequest {

    private final URI resourceUri;
    private final List<URI> affiliations;

    public CreateRequest(URI resourceUri, List<URI> affiliations) {
        this.resourceUri = resourceUri;
        this.affiliations = affiliations;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(resourceUri, affiliations);
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CreateRequest that = (CreateRequest) o;
        return Objects.equals(resourceUri, that.resourceUri) && Objects.equals(affiliations,
                                                                               that.affiliations);
    }

    public URI getResourceUri() {
        return resourceUri;
    }

    public List<URI> getAffiliations() {
        return affiliations;
    }
}
