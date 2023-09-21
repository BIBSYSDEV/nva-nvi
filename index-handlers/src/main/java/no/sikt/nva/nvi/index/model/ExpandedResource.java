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

public class ExpandedResource {

    public static final String TOP_LEVEL_ORGANIZATION = "topLevelOrganization";
    private final List<Organization> topLevelOrganization;

    @JsonCreator
    public ExpandedResource(@JsonProperty(TOP_LEVEL_ORGANIZATION) Object topLevelOrganization)
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

        public static final String ID = "id";
        public static final String LABELS = "labels";
        public static final String HAS_PART = "hasPart";
        private final String id;
        private final List<Organization> affiliations;
        private final Map<String, String> labels;

        @JsonCreator
        private Organization(@JsonProperty(ID) String id, @JsonProperty(HAS_PART) Object affiliations,
                             @JsonProperty(LABELS) Map<String, String> labels) throws JsonProcessingException {
            this.id = id;
            this.labels = labels;
            var string = dtoObjectMapper.writeValueAsString(affiliations);
            if (affiliations instanceof Map) {
                var organizations = JsonUtils.dtoObjectMapper.readValue(string, Organization.class);
                this.affiliations = List.of(organizations);
            } else {
                var organizations = JsonUtils.dtoObjectMapper.readValue(string, Organization[].class);
                this.affiliations = nonNull(affiliations) ? Arrays.stream(organizations).toList() : List.of();
            }
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
