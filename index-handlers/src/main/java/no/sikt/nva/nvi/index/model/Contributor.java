package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import java.util.Objects;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(
    use = Id.NAME,
    property = "type")
@JsonTypeName("Contributor")
public class Contributor {

    private final String id;
    private final String name;
    private final String orcid;
    private final String role;
    private final List<Affiliation> affiliations;
    private final boolean isNviContributor;

    public Contributor(String id, String name, String orcid, String role, List<Affiliation> affiliations,
                       boolean isNviContributor) {
        this.id = id;
        this.name = name;
        this.orcid = orcid;
        this.role = role;
        this.affiliations = affiliations;
        this.isNviContributor = isNviContributor;
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

    public boolean isNviContributor() {
        return isNviContributor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, orcid, role, affiliations, isNviContributor);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (Contributor) obj;
        return Objects.equals(this.id, that.id) &&
               Objects.equals(this.name, that.name) &&
               Objects.equals(this.orcid, that.orcid) &&
               Objects.equals(this.role, that.role) &&
               Objects.equals(this.affiliations, that.affiliations) &&
               this.isNviContributor == that.isNviContributor;
    }

    @Override
    public String toString() {
        return "Contributor[" +
               "id=" + id + ", " +
               "name=" + name + ", " +
               "orcid=" + orcid + ", " +
               "role=" + role + ", " +
               "affiliations=" + affiliations + ", " +
               "isNviContributor=" + isNviContributor + ']';
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
            return new Contributor(id, name, orcid, role, affiliations, isNviContributor);
        }
    }
}
