package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("Organization")
public record NviOrganization(@JsonProperty("id") String id,
                              @JsonProperty("partOf") List<String> partOf) implements OrganizationType {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private List<String> partOf;

        private Builder() {
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withPartOf(List<String> partOf) {
            this.partOf = partOf;
            return this;
        }

        public NviOrganization build() {
            return new NviOrganization(id, partOf);
        }
    }
}
