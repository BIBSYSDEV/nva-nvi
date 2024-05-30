package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("Organization")
public record NviOrganization(@JsonProperty("id") URI id,
                              @JsonProperty("partOf") List<String> partOf,
                              @JsonIgnore List<URI> partOfIds) implements OrganizationType {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI id;
        private List<String> partOf;
        List<URI> partOfIds;

        private Builder() {
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withPartOf(List<String> partOf) {
            this.partOf = partOf;
            return this;
        }

        public Builder withPartOfIds(List<URI> partOfIds) {
            this.partOfIds = partOfIds;
            return this;
        }

        public NviOrganization build() {
            return new NviOrganization(id, partOf, partOfIds);
        }
    }
}
