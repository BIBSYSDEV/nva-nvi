package no.sikt.nva.nvi.events.cristin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import nva.commons.core.JacocoGenerated;

public class ScientificPerson {

    @JsonProperty("personlopenr")
    private String cristinPersonIdentifier;
    @JsonProperty("institusjonsnr")
    private String institutionIdentifier;
    @JsonProperty("avdnr")
    private String departmentIdentifier;
    @JsonProperty("undavdnr")
    private String subDepartmentIdentifier;
    @JsonProperty("gruppenr")
    private String groupIdentifier;

    @JacocoGenerated
    @JsonCreator
    private ScientificPerson() {

    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String cristinPersonIdentifier;
        private String institutionIdentifier;
        private String departmentIdentifier;
        private String subDepartmentIdentifier;
        private String groupIdentifier;

        private Builder() {
        }

        public Builder withCristinPersonIdentifier(String cristinPersonIdentifier) {
            this.cristinPersonIdentifier = cristinPersonIdentifier;
            return this;
        }

        public Builder withInstitutionIdentifier(String institutionIdentifier) {
            this.institutionIdentifier = institutionIdentifier;
            return this;
        }

        public Builder withDepartmentIdentifier(String departmentIdentifier) {
            this.departmentIdentifier = departmentIdentifier;
            return this;
        }

        public Builder withSubDepartmentIdentifier(String subDepartmentIdentifier) {
            this.subDepartmentIdentifier = subDepartmentIdentifier;
            return this;
        }

        public Builder withGroupIdentifier(String groupIdentifier) {
            this.groupIdentifier = groupIdentifier;
            return this;
        }

        public ScientificPerson build() {
            ScientificPerson scientificPerson = new ScientificPerson();
            scientificPerson.groupIdentifier = this.groupIdentifier;
            scientificPerson.institutionIdentifier = this.institutionIdentifier;
            scientificPerson.subDepartmentIdentifier = this.subDepartmentIdentifier;
            scientificPerson.departmentIdentifier = this.departmentIdentifier;
            scientificPerson.cristinPersonIdentifier = this.cristinPersonIdentifier;
            return scientificPerson;
        }
    }
}