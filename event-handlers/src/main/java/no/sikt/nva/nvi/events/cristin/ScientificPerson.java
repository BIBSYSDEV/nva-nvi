package no.sikt.nva.nvi.events.cristin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import nva.commons.core.JacocoGenerated;

public final class ScientificPerson {

    public static final String AFFILIATION_DELIMITER = ".";
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
    @JsonProperty("forfattervekt")
    private String authorPointsForAffiliation;
    @JsonProperty("vektingstall")
    private String publicationTypeLevelPoints;
    @JsonProperty("faktortall_samarbeid")
    private String collaborationFactor;

    @JacocoGenerated
    @JsonCreator
    private ScientificPerson() {

    }

    public static Builder builder() {
        return new Builder();
    }

    public String getCollaborationFactor() {
        return collaborationFactor;
    }

    public String getAuthorPointsForAffiliation() {
        return authorPointsForAffiliation;
    }

    public String getPublicationTypeLevelPoints() {
        return publicationTypeLevelPoints;
    }

    public String getOrganization() {
        return institutionIdentifier
               + AFFILIATION_DELIMITER
               + departmentIdentifier
               + AFFILIATION_DELIMITER
               + subDepartmentIdentifier
               + AFFILIATION_DELIMITER
               + groupIdentifier;
    }

    public String getCristinPersonIdentifier() {
        return cristinPersonIdentifier;
    }

    public String getInstitutionIdentifier() {
        return institutionIdentifier;
    }

    public String getDepartmentIdentifier() {
        return departmentIdentifier;
    }

    public String getSubDepartmentIdentifier() {
        return subDepartmentIdentifier;
    }

    public String getGroupIdentifier() {
        return groupIdentifier;
    }

    public static final class Builder {

        private String cristinPersonIdentifier;
        private String institutionIdentifier;
        private String departmentIdentifier;
        private String subDepartmentIdentifier;
        private String groupIdentifier;
        private String authorPointsForAffiliation;
        private String publicationTypeLevelPoints;
        private String collaborationFactor;

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

        public Builder withAuthorPointsForAffiliation(String authorPoints) {
            this.authorPointsForAffiliation = authorPoints;
            return this;
        }

        public Builder withPublicationTypeLevelPoints(String publicationPoints) {
            this.publicationTypeLevelPoints = publicationPoints;
            return this;
        }

        public Builder withCollaborationFactor(String collaborationFactor) {
            this.collaborationFactor = collaborationFactor;
            return this;
        }

        public ScientificPerson build() {
            ScientificPerson scientificPerson = new ScientificPerson();
            scientificPerson.groupIdentifier = this.groupIdentifier;
            scientificPerson.institutionIdentifier = this.institutionIdentifier;
            scientificPerson.subDepartmentIdentifier = this.subDepartmentIdentifier;
            scientificPerson.departmentIdentifier = this.departmentIdentifier;
            scientificPerson.cristinPersonIdentifier = this.cristinPersonIdentifier;
            scientificPerson.authorPointsForAffiliation = this.authorPointsForAffiliation;
            scientificPerson.publicationTypeLevelPoints = this.publicationTypeLevelPoints;
            scientificPerson.collaborationFactor = this.collaborationFactor;
            return scientificPerson;
        }
    }
}