package no.sikt.nva.nvi.common;

import java.util.List;

public class Foundation {

    private final String instanceType;
    private final Level level;
    private final List<Creator> creators;
    private final boolean international;
    private final int creators;

    public Foundation(String instanceType, Level level, List<Creator> creators, boolean international, int creators1) {
        this.instanceType = instanceType;
        this.level = level;
        this.creators = creators;
        this.international = international;
        this.creators = creators1;
    }

    public static class Builder {

        private String instanceType;
        private Level level;
        private List<Creator> creators;
        private boolean international;
        private int creators1;

        public Builder setInstanceType(String instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public Builder setLevel(Level level) {
            this.level = level;
            return this;
        }

        public Builder setCreators(List<Creator> creators) {
            this.creators = creators;
            return this;
        }

        public Builder setInternational(boolean international) {
            this.international = international;
            return this;
        }

        public Builder setCreators1(int creators1) {
            this.creators1 = creators1;
            return this;
        }

        public Foundation build() {
            return new Foundation(this);
        }
    }
}
