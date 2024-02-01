package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(
    use = Id.NAME,
    property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NviContributor.class, name = "NviContributor")
})
public class Contributor {

    private final String id;
    private final String name;
    private final String orcid;
    private final String role;
    private final List<Affiliation> affiliations;

    @JsonCreator
    public Contributor(@JsonProperty("id") String id,
                       @JsonProperty("name") String name,
                       @JsonProperty("orcid") String orcid,
                       @JsonProperty("role") String role,
                       @JsonProperty("affiliations") List<Affiliation> affiliations) {
        this.id = id;
        this.name = name;
        this.orcid = orcid;
        this.role = role;
        this.affiliations = affiliations;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String orcid() {
        return orcid;
    }

    public String role() {
        return role;
    }

    public List<Affiliation> affiliations() {
        return affiliations;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(id, name, orcid, role, affiliations);
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
        Contributor that = (Contributor) o;
        return Objects.equals(id, that.id)
               && Objects.equals(name, that.name)
               && Objects.equals(orcid, that.orcid)
               && Objects.equals(role, that.role)
               && Objects.equals(affiliations, that.affiliations);
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
