package no.sikt.nva.nvi.common.model;

import java.util.List;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public class Foundation {

    private final String instanceType;
    private final Level level;
    private final List<Creator> creators;
    private final boolean international;
    private final int totalCreators;

    public Foundation(Builder builder) {
        this.instanceType = builder.instanceType;
        this.level = builder.level;
        this.creators = builder.creators;
        this.international = builder.international;
        this.totalCreators = builder.totalCreators;
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(getInstanceType(), getLevel(), getCreators(), isInternational(), getTotalCreators());
    }

    @JacocoGenerated
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Foundation that = (Foundation) o;
        return isInternational() == that.isInternational()
               && getTotalCreators() == that.getTotalCreators()
               && Objects.equals(getInstanceType(), that.getInstanceType())
               && getLevel() == that.getLevel()
               && Objects.equals(getCreators(), that.getCreators());
    }

    public String getInstanceType() {
        return instanceType;
    }

    public Level getLevel() {
        return level;
    }

    public List<Creator> getCreators() {
        return creators;
    }

    public boolean isInternational() {
        return international;
    }

    public int getTotalCreators() {
        return totalCreators;
    }

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
            return new Foundation(this);
        }
    }
}
