package no.sikt.nva.nvi.index.model.report;

public enum InstitutionReportHeader {
    REPORTING_YEAR("ARSTALL"),
    PUBLICATION_IDENTIFIER("NVAID"),
    PUBLISHED_YEAR("ARSTALL_REG"),
    INSTITUTION_APPROVAL_STATUS("STATUS_KONTROLLERT"),
    PUBLICATION_INSTANCE("PUBLIKASJONSFORM"),
    CONTRIBUTOR_IDENTIFIER("PERSONLOPENR"),
    INSTITUTION_ID("INSTITUSJONSNR"),
    FACULTY_ID("AVDNR"),
    DEPARTMENT_ID("UNDAVDNR"),
    GROUP_ID("GRUPPENR"),
    LAST_NAME("ETTERNAVN"),
    FIRST_NAME("FORNAVN"),
    PUBLICATION_TITLE("VA_TITTEL"),
    GLOBAL_STATUS("RAPPORTSTATUS"),
    PUBLICATION_CHANNEL_LEVEL_POINTS("VEKTINGSTALL"),
    INTERNATIONAL_COLLABORATION_FACTOR("FAKTORTALL_SAMARBEID"),
    AUTHOR_SHARE_COUNT("FORFATTERDEL"),
    POINTS_FOR_AFFILIATION("FORFATTERVEKT");

    private final String value;

    InstitutionReportHeader(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}