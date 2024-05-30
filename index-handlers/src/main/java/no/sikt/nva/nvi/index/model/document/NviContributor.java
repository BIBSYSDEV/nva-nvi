package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.paths.UriWrapper;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record NviContributor(@JsonProperty("id") String id,
                             @JsonProperty("name") String name,
                             @JsonProperty("orcid") String orcid,
                             @JsonProperty("role") String role,
                             @JsonProperty("affiliations") List<OrganizationType> affiliations)
    implements ContributorType {

    public static Builder builder() {
        return new Builder();
    }

    public List<URI> organizationsPartOf(URI topLevelOrg) {
        return affiliations.stream()
                   .filter(organization -> organization instanceof NviOrganization)
                   .map(organizationType -> (NviOrganization) organizationType)
                   .filter(organization -> organization.partOfIds().contains(topLevelOrg))
                   .flatMap(nviOrganization -> nviOrganization.partOfIds().stream())
                   .toList();
    }

    public static final class Builder {

        private String id;
        private String name;
        private String orcid;
        private String role;
        private List<OrganizationType> affiliations;

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

        public Builder withAffiliations(List<OrganizationType> affiliations) {
            this.affiliations = affiliations;
            return this;
        }

        public NviContributor build() {
            return new NviContributor(id, name, orcid, role, affiliations);
        }
    }
}
