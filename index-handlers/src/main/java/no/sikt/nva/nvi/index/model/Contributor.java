package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(
    use = Id.NAME,
    property = "type")
public record Contributor(@JsonProperty("id") String id,
                          @JsonProperty("name") String name,
                          @JsonProperty("orcid") String orcid,
                          @JsonProperty("role") String role,
                          @JsonProperty("affiliations") List<Affiliation> affiliations)
    implements ContributorType {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private String name;
        private String orcid;
        private String role;
        private List<Affiliation> affiliations;
        private boolean isNviContributor;

        private Builder() {
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withOrcid(String orcid) {
            this.orcid = orcid;
            return this;
        }

        public Builder withRole(String role) {
            this.role = role;
            return this;
        }

        public Builder withAffiliations(List<Affiliation> affiliations) {
            this.affiliations = affiliations;
            return this;
        }

        public Builder withIsNviContributor(boolean isNviContributor) {
            this.isNviContributor = isNviContributor;
            return this;
        }

        public Contributor build() {
            return new Contributor(id, name, orcid, role, affiliations);
        }
    }
}
