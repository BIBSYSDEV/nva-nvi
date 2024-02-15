package no.sikt.nva.nvi.events.cristin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import nva.commons.core.JacocoGenerated;

public final class ScientificResource {

    @JsonProperty("h_dbh_forskres_forfatter")
    private List<ScientificPerson> creators;
    @JsonProperty("kvalitetsnivakode")
    private String qualityCode;
    @JsonProperty("arstall")
    private String reportedYear;

    @JacocoGenerated
    @JsonCreator
    private ScientificResource() {
    }

    public static Builder build() {
        return new Builder();
    }

    @JacocoGenerated
    public List<ScientificPerson> getCreators() {
        return creators;
    }

    public String getQualityCode() {
        return qualityCode;
    }

    public String getReportedYear() {
        return reportedYear;
    }

    public static final class Builder {

        private List<ScientificPerson> creators;
        private String qualityCode;
        private String reportedYear;

        private Builder() {
        }

        public Builder withScientificPeople(List<ScientificPerson> creators) {
            this.creators = creators;
            return this;
        }

        public Builder withQualityCode(String qualityCode) {
            this.qualityCode = qualityCode;
            return this;
        }

        public Builder withReportedYear(String reportedYear) {
            this.reportedYear = reportedYear;
            return this;
        }

        public ScientificResource build() {
            ScientificResource scientificResource = new ScientificResource();
            scientificResource.creators = this.creators;
            scientificResource.qualityCode = this.qualityCode;
            scientificResource.reportedYear = this.reportedYear;
            return scientificResource;
        }
    }
}