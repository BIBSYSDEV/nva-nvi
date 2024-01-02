package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class ExpandedResource {

    public static final String TOP_LEVEL_ORGANIZATION = "topLevelOrganizations";
    private final List<Organization> topLevelOrganization;

    @JsonCreator
    public ExpandedResource(@JsonProperty(TOP_LEVEL_ORGANIZATION) List<Organization> topLevelOrganizations) {
        this.topLevelOrganization = topLevelOrganizations;
    }

    public List<Organization> getTopLevelOrganization() {
        return topLevelOrganization;
    }

    public static class Organization {

        public static final String ID = "id";
        public static final String LABELS = "labels";
        private final String id;
        private final Map<String, String> labels;

        @JsonCreator
        private Organization(@JsonProperty(ID) String id,
                             @JsonProperty(LABELS) Map<String, String> labels) {
            this.id = id;
            this.labels = labels;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public String getId() {
            return id;
        }
    }
}
