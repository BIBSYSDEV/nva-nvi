package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        var partOfIds = affiliationsPartOf(topLevelOrg)
                            .flatMap(nviOrganization -> nviOrganization.partOf().stream())
                            .collect(Collectors.toCollection(ArrayList::new));
        partOfIds.addAll(affiliationsIdsPartOf(topLevelOrg));
        return partOfIds;
    }

    public List<URI> affiliationsIdsPartOf(URI topLevelOrg) {
        return affiliationsPartOf(topLevelOrg)
                   .map(NviOrganization::id)
                   .toList();
    }

    private Stream<NviOrganization> affiliationsPartOf(URI topLevelOrg) {
        return affiliations.stream()
                   .filter(organization -> organization instanceof NviOrganization)
                   .map(organizationType -> (NviOrganization) organizationType)
                   .filter(organization -> organization.partOf().contains(topLevelOrg));
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
