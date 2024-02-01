package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type")
@JsonTypeName("Organization")
public record Affiliation(String id,
                          List<String> partOf,
                          boolean isNviAffiliation) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private List<String> partOf;
        private boolean isNviAffiliation;

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

        public Builder withIsNviAffiliation(boolean isNviAffiliation) {
            this.isNviAffiliation = isNviAffiliation;
            return this;
        }

        public Affiliation build() {
            return new Affiliation(id, partOf, isNviAffiliation);
        }
    }
}
