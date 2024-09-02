package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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

    public List<URI> getOrganizationsPartOf(URI topLevelOrg) {
        var organizationsPartOfTopLevelOrg = new ArrayList<URI>();
        organizationsPartOfTopLevelOrg.addAll(getOrganizationsAboveAffiliationInOrgHierarchy(topLevelOrg));
        organizationsPartOfTopLevelOrg.addAll(getAffiliationsIdsPartOf(topLevelOrg));
        organizationsPartOfTopLevelOrg.add(topLevelOrg);
        return organizationsPartOfTopLevelOrg.stream().toList();
    }

    public List<NviOrganization> nviAffiliations() {
        return affiliations.stream()
                   .filter(NviOrganization.class::isInstance)
                   .map(NviOrganization.class::cast)
                   .toList();
    }

    public Stream<NviOrganization> getAffiliationsPartOf(URI topLevelOrg) {
        return nviAffiliations().stream()
                   .filter(organization -> organization.partOf().contains(topLevelOrg));
    }

    private List<URI> getOrganizationsAboveAffiliationInOrgHierarchy(URI topLevelOrg) {
        return getAffiliationsPartOf(topLevelOrg)
                   .flatMap(nviOrganization -> nviOrganization.partOf().stream())
                   .toList();
    }

    private List<URI> getAffiliationsIdsPartOf(URI topLevelOrg) {
        return getAffiliationsPartOf(topLevelOrg)
                   .map(NviOrganization::id)
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
