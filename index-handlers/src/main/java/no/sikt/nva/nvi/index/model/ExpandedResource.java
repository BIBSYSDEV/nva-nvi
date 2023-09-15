package no.sikt.nva.nvi.index.model;

import static java.util.Objects.nonNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import no.unit.nva.commons.json.JsonUtils;

public class Publication {

    public static final String TOP_LEVEL_ORGANIZATION = "topLevelOrganization";
    private final List<Organization> topLevelOrganization;

    @JsonCreator
    public Publication(@JsonProperty(TOP_LEVEL_ORGANIZATION) Object topLevelOrganization)
        throws JsonProcessingException {
        var string = dtoObjectMapper.writeValueAsString(topLevelOrganization);
        if (topLevelOrganization instanceof Map) {
            var organizations = JsonUtils.dtoObjectMapper.readValue(string, Organization.class);
            this.topLevelOrganization = List.of(organizations);
        } else {
            var organizations = JsonUtils.dtoObjectMapper.readValue(string, Organization[].class);
            this.topLevelOrganization = Arrays.stream(organizations).toList();
        }
    }

    public List<Organization> getTopLevelOrganization() {
        return topLevelOrganization;
    }

    public static class Organization {

        private final String id;
        private final List<Organization> affiliations;
        private final Map<String, String> labels;

        @JsonCreator
        private Organization(@JsonProperty("id") String id, @JsonProperty("hasPart") List<Organization> affiliations,
                             @JsonProperty("labels") Map<String, String> labels) {
            this.id = id;
            this.affiliations = affiliations;
            this.labels = labels;
        }

        @JsonIgnore
        public List<String> getAllAffiliations() {
            var list = new ArrayList<String>();
            list.add(id);
            if (nonNull(this.getAffiliations())) {
                var organizations = getAffiliations();
                while (!organizations.isEmpty()) {
                    var affiliations = organizations;
                    list.addAll(affiliations.stream().map(Organization::getId).toList());
                    organizations = getAffiliations(affiliations);
                }
            }
            return list;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public boolean hasAffiliation(String affiliationIdentifier) {
            return getAllAffiliations().stream().anyMatch(identifier -> identifier.equals(affiliationIdentifier));
        }

        public List<Organization> getAffiliations() {
            return affiliations;
        }

        private List<Organization> getAffiliations(List<Organization> affiliations) {
            return affiliations.stream()
                       .filter(Organization::hasMoreOrganizations)
                       .map(Organization::getAffiliations)
                       .flatMap(Collection::stream)
                       .toList();
        }

        public String getId() {
            return id;
        }

        private static boolean hasMoreOrganizations(Organization org) {
            return nonNull(org.getAffiliations()) && !org.getAffiliations().isEmpty();
        }
    }
}
