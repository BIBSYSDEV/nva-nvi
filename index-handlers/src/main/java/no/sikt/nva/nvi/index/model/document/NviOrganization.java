package no.sikt.nva.nvi.index.model.document;

import static java.util.Objects.isNull;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import nva.commons.core.paths.UriWrapper;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("Organization")
public record NviOrganization(@JsonProperty("id") URI id,
                              @JsonProperty("identifier") String identifier,
                              @JsonProperty("partOf") List<URI> partOf,
                              @JsonProperty("partOfIdentifiers") List<String> partOfIdentifiers)
    implements OrganizationType {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<URI> partOf() {
        return isNull(partOf) ? List.of() : partOf;
    }

    public static final class Builder {

        private URI id;
        private String identifier;
        private List<URI> partOf;
        private List<String> partOfIdentifiers;

        private Builder() {
        }

        public Builder withId(URI id) {
            this.id = id;
            this.identifier = getLastPathElement(id);
            return this;
        }

        public Builder withPartOf(List<URI> partOf) {
            this.partOf = partOf;
            this.partOfIdentifiers = partOf.stream().map(Builder::getLastPathElement).toList();
            return this;
        }

        public NviOrganization build() {
            return new NviOrganization(id, identifier, partOf, partOfIdentifiers);
        }

        private static String getLastPathElement(URI uri) {
            return UriWrapper.fromUri(uri).getLastPathElement();
        }
    }
}
