package no.sikt.nva.nvi.test;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.LABELS_FIELD;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Map;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
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
            affiliationNode.put(ID_FIELD, id.toString());
        }
        if (nonNull(countryCode)) {
            affiliationNode.put(COUNTRY_CODE_FIELD, countryCode);
        }
        if (nonNull(labels)) {
            var labelsNode = objectMapper.createObjectNode();
            labels.forEach(labelsNode::put);
            affiliationNode.set(LABELS_FIELD, labelsNode);
        }

        return affiliationNode;
    }

    @JacocoGenerated
    public static final class Builder {

        private URI id = randomUri();
        private String countryCode = COUNTRY_CODE_NORWAY;
        private Map<String, String> labels = Map.of();

        private Builder() {
        }

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

        public ExpandedAffiliation build() {
            return new ExpandedAffiliation(id, countryCode, labels);
        }
    }
}
