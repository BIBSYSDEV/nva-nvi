package no.sikt.nva.nvi.events.cristin;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.LocalDate;
import nva.commons.core.JacocoGenerated;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public class CristinLocale {

    public static final String OWNER_CODE_FIELD = "eierkode";
    public static final String INSTITUTION_IDENTIFIER_FIELD = "institusjonsnr";
    public static final String DEPARTMENT_IDENTIFIER_FIELD = "avdnr";
    public static final String SUB_DEPARTMENT_IDENTIFIER_FIELD = "undavdnr";
    public static final String GROUP_IDENTIFIER_FIELD = "gruppenr";
    public static final String CRISTIN = "cristin";
    public static final String CONTROLLED_BY_FIELD = "brukernavn_kontrollert";
    public static final String DATE_CONTROLLED_FIELD = "dato_kontrollert";
    public static final String CONTROL_STATUS_FIELD = "status_kontrollert";
    @JsonProperty(OWNER_CODE_FIELD)
    private String ownerCode;

    @JsonProperty(INSTITUTION_IDENTIFIER_FIELD)
    private String institutionIdentifier;

    @JsonProperty(DEPARTMENT_IDENTIFIER_FIELD)
    private String departmentIdentifier;

    @JsonProperty(SUB_DEPARTMENT_IDENTIFIER_FIELD)
    private String subDepartmentIdentifier;

    @JsonProperty(GROUP_IDENTIFIER_FIELD)
    private String groupIdentifier;

    @JsonProperty(CONTROLLED_BY_FIELD)
    private String controlledBy;

    @JsonProperty(DATE_CONTROLLED_FIELD)
    private LocalDate dateControlled;

    @JsonProperty(CONTROL_STATUS_FIELD)
    private String controlStatus;

    private CristinLocale() {

    }

    public static Builder builder() {
        return new Builder();
    }

    public String getOwnerCode() {
        return ownerCode;
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

    public String getControlledBy() {
        return controlledBy;
    }

    public LocalDate getDateControlled() {
        return dateControlled;
    }

    public String getControlStatus() {
        return controlStatus;
    }

    @JacocoGenerated
    public static final class Builder {

        private String ownerCode;
        private String institutionIdentifier;
        private String departmentIdentifier;
        private String subDepartmentIdentifier;
        private String groupIdentifier;
        private String controlledBy;
        private LocalDate dateControlled;
        private String controlStatus;

        private Builder() {
        }

        public Builder withOwnerCode(String ownerCode) {
            this.ownerCode = ownerCode;
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

        public Builder withControlledBy(String controlledBy) {
            this.controlledBy = controlledBy;
            return this;
        }

        public Builder withDateControlled(LocalDate dateControlled) {
            this.dateControlled = dateControlled;
            return this;
        }

        public Builder withControlStatus(String controlStatus) {
            this.controlStatus = controlStatus;
            return this;
        }

        public CristinLocale build() {
            CristinLocale cristinLocale = new CristinLocale();
            cristinLocale.institutionIdentifier = this.institutionIdentifier;
            cristinLocale.subDepartmentIdentifier = this.subDepartmentIdentifier;
            cristinLocale.controlledBy = this.controlledBy;
            cristinLocale.departmentIdentifier = this.departmentIdentifier;
            cristinLocale.dateControlled = this.dateControlled;
            cristinLocale.groupIdentifier = this.groupIdentifier;
            cristinLocale.ownerCode = this.ownerCode;
            cristinLocale.controlStatus = this.controlStatus;
            return cristinLocale;
        }
    }
}
