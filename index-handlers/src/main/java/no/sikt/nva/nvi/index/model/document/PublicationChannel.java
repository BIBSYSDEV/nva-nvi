package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.Arrays;

@JsonSerialize
public record PublicationChannel(URI id,
                                 String type,
                                 ScientificValue scientificValue,
                                 String name) {

    public static Builder builder() {
        return new Builder();
    }

    public enum ScientificValue {
        LEVEL_ONE("LevelOne"),
        LEVEL_TWO("LevelTwo");
        @JsonValue
        private final String value;

        ScientificValue(String value) {
            this.value = value;
        }

        public static ScientificValue parse(String value) {
            return Arrays.stream(ScientificValue.values())
                       .filter(type -> type.getValue().equalsIgnoreCase(value))
                       .findFirst()
                       .orElseThrow(ScientificValue::getIllegalArgumentException);
        }

        public String getValue() {
            return value;
        }

        private static IllegalArgumentException getIllegalArgumentException() {
            return new IllegalArgumentException(String.format("Unknown value. Valid values are: %s",
                                                              Arrays.toString(ScientificValue.values())));
        }
    }

    public static final class Builder {

        private URI id;
        private String type;
        private ScientificValue scientificValue;
        private String name;

        private Builder() {
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        public Builder withScientificValue(ScientificValue scientificValue) {
            this.scientificValue = scientificValue;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public PublicationChannel build() {
            return new PublicationChannel(id, type, scientificValue, name);
        }
    }
}
