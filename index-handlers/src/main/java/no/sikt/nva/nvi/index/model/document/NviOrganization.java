package no.sikt.nva.nvi.index.model.document;

import static java.util.Objects.isNull;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
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
                              @JsonProperty("partOf") List<URI> partOf) implements OrganizationType {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<URI> partOf() {
        return isNull(partOf) ? List.of() : partOf;
    }

    public static final class Builder {

        private URI id;
        private List<URI> partOf;

        private Builder() {
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withPartOf(List<URI> partOf) {
            this.partOf = partOf;
            return this;
        }

        public NviOrganization build() {
            return new NviOrganization(id, partOf);
        }
    }
}
