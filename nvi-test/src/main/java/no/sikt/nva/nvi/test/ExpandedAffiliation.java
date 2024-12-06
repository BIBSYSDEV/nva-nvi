package no.sikt.nva.nvi.test;

import static java.util.Objects.nonNull;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Map;

public record ExpandedAffiliation(URI id, String countryCode, Map<String, String> labels) {
    public static Builder builder() {
        return new Builder();
    }

    public static Builder from(ExpandedAffiliation other) {
        return new Builder(other);
    }

    public ObjectNode asObjectNode() {
        var affiliationNode = objectMapper.createObjectNode();

        if (nonNull(id)) {
            affiliationNode.put("id", id.toString());
        }
        if (nonNull(countryCode)) {
            affiliationNode.put("countryCode", countryCode);
        }
        if (nonNull(labels)) {
            var labelsNode = objectMapper.createObjectNode();
            labels.forEach(labelsNode::put);
            affiliationNode.set("labels", labelsNode);
        }

        return affiliationNode;
    }

    public static final class Builder {

        private URI id;
        private String countryCode = "NO";
        private Map<String, String> labels = Map.of();

        private Builder() {}

        private Builder(ExpandedAffiliation other) {
            this.id = other.id();
            this.countryCode = other.countryCode();
            this.labels = other.labels();
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withCountryCode(String countryCode) {
            this.countryCode = countryCode;
            return this;
        }

        public Builder withLabels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public ExpandedAffiliation build() {
            return new ExpandedAffiliation(id, countryCode, labels);
        }
    }
}
