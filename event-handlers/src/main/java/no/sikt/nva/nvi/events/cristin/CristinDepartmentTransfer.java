package no.sikt.nva.nvi.events.cristin;

import com.fasterxml.jackson.annotation.JsonProperty;
import nva.commons.core.JacocoGenerated;

public class CristinDepartmentTransfer {

    @JsonProperty("INSTITUSJONSNR_FRA")
    private String fromInstitutionIdentifier;

    @JsonProperty("AVDNR_FRA")
    private String fromAvdNr;

    @JsonProperty("UNDAVDNR_FRA")
    private String fromUndAvdNr;

    @JsonProperty("GRUPPENR_FRA")
    private String fromGruppeNr;
    @JsonProperty("INSTITUSJONSNR_TIL")
    private String toInstitutionIdentifier;
    @JsonProperty("AVDNR_TIL")
    private String toAvdNr;
    @JsonProperty("UNDAVDNR_TIL")
    private String toUndAvdNr;
    @JsonProperty("GRUPPENR_TIL")
    private String toGruppeNr;
    @JsonProperty("INSTITUSJONSNAVN_BOKMAL")
    private String institutionNameBokmal;

    public CristinDepartmentTransfer() {
    }

    @JacocoGenerated
    public String getInstitutionNameBokmal() {
        return institutionNameBokmal;
    }

    @JacocoGenerated
    public String getToGruppeNr() {
        return toGruppeNr;
    }

    @JacocoGenerated
    public String getToUndAvdNr() {
        return toUndAvdNr;
    }

    @JacocoGenerated
    public String getToAvdNr() {
        return toAvdNr;
    }

    public String getToInstitutionIdentifier() {
        return toInstitutionIdentifier;
    }

    @JacocoGenerated
    public String getFromGruppeNr() {
        return fromGruppeNr;
    }

    @JacocoGenerated
    public String getFromUndAvdNr() {
        return fromUndAvdNr;
    }

    @JacocoGenerated
    public String getFromAvdNr() {
        return fromAvdNr;
    }

    public String getFromInstitutionIdentifier() {
        return fromInstitutionIdentifier;
    }
}
