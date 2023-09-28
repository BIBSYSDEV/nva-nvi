package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Affiliation(String id,
                          List<String> partOf) {

    public static final class Builder {

        private String id;
        private List<String> partOf;

        public Builder() {
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withPartOf(List<String> partOf) {
            this.partOf = partOf;
            return this;
        }

        public Affiliation build() {
            return new Affiliation(id, partOf);
        }
    }
}
