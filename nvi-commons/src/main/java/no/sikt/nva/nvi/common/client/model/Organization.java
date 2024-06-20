package no.sikt.nva.nvi.common.client.model;

import static java.util.Objects.nonNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.SingletonCollector;

public record Organization(@JsonProperty(ID) URI id,
                           @JsonProperty(PART_OF) List<Organization> partOf,
                           @JsonProperty(LABELS) Map<String, String> labels,
                           @JsonProperty(TYPE) String type,
                           @JsonProperty(CONTEXT) String context)
    implements JsonSerializable {

    private static final String ID = "id";
    private static final String PART_OF = "partOf";
    private static final String LABELS = "labels";
    private static final String TYPE = "type";
    private static final String CONTEXT = "@context";

    public static Organization from(String json) throws JsonProcessingException {
        return dtoObjectMapper.readValue(json, Organization.class);
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonIgnore
    public Organization getTopLevelOrg() {
        if (nonNull(partOf()) && !partOf().isEmpty()) {

            var organization = partOf().stream().collect(SingletonCollector.collect());

            while (hasPartOf(organization)) {
                organization = organization.partOf().stream().collect(SingletonCollector.collect());
            }

            return organization;
        }

        return this;
    }

    public String asJsonString() throws JsonProcessingException {
        return dtoObjectMapper.writeValueAsString(this);
    }

    private static boolean hasPartOf(Organization org) {
        return nonNull(org.partOf()) && !org.partOf().isEmpty();
    }

    public static final class Builder {

        private URI id;
        private List<Organization> partOf;
        private Map<String, String> labels;
        private String type;
        private String context;

        private Builder() {
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withPartOf(List<Organization> partOf) {
            this.partOf = partOf;
            return this;
        }

        public Builder withLabels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        public Builder withContext(String context) {
            this.context = context;
            return this;
        }

        public Organization build() {
            return new Organization(id, partOf, labels, type, context);
        }
    }
}
