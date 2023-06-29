package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Foundation(String instanceType,
                         Level level,
                         List<Creator> creators,
                         boolean international,
                         int totalCreators) {

    public static class Builder {

        private String instanceType;
        private Level level;
        private List<Creator> creators;
        private boolean international;
        private int totalCreators;

        public Builder() {
        }

        public Builder withInstanceType(String instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public Builder withLevel(Level level) {
            this.level = level;
            return this;
        }

        public Builder withCreators(List<Creator> creators) {
            this.creators = creators;
            return this;
        }

        public Builder withInternational(boolean international) {
            this.international = international;
            return this;
        }

        public Builder withTotalCreator(int totalCreators) {
            this.totalCreators = totalCreators;
            return this;
        }

        public Foundation build() {
            return new Foundation(instanceType, level, creators, international, totalCreators);
        }
    }
}
