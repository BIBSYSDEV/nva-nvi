package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.Arrays;

@JsonSerialize
public record PublicationChannel(@JsonProperty("id") URI id,
                                 @JsonProperty("type") String type,
                                 @JsonProperty("scientificValue") ScientificValue scientificValue) {

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
            return switch (value) {
                case "LevelOne" -> LEVEL_ONE;
                case "LevelTwo" -> LEVEL_TWO;
                default -> throw new IllegalArgumentException("Unknown value. Expected one of: " + Arrays.toString(
                    values()));
            };
        }

        public String getValue() {
            return value;
        }
    }

    public static final class Builder {

        private URI id;
        private String type;

        private ScientificValue scientificValue;

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

        public PublicationChannel build() {
            return new PublicationChannel(id, type, scientificValue);
        }
    }
}
