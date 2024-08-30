package no.sikt.nva.nvi.index.apigateway;

import java.util.List;

public enum NviInstitutionStatusHeader {
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

    NviInstitutionStatusHeader(String value) {
        this.value = value;
    }

    public static List<String> getExpectedHeaders() {
        return List.of(REPORTING_YEAR.value,
                       PUBLICATION_IDENTIFIER.value,
                       PUBLISHED_YEAR.value,
                       INSTITUTION_APPROVAL_STATUS.value,
                       PUBLICATION_INSTANCE.value,
                       CONTRIBUTOR_IDENTIFIER.value,
                       INSTITUTION_ID.value,
                       FACULTY_ID.value,
                       DEPARTMENT_ID.value,
                       GROUP_ID.value,
                       LAST_NAME.value,
                       FIRST_NAME.value,
                       PUBLICATION_TITLE.value,
                       GLOBAL_STATUS.value,
                       PUBLICATION_CHANNEL_LEVEL_POINTS.value,
                       INTERNATIONAL_COLLABORATION_FACTOR.value,
                       AUTHOR_SHARE_COUNT.value,
                       POINTS_FOR_AFFILIATION.value);
    }
}
