package no.sikt.nva.nvi.index.model.report;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public enum InstitutionReportHeader {
    REPORTING_YEAR("ARSTALL", 0),
    PUBLICATION_IDENTIFIER("NVAID", 1),
    PUBLISHED_YEAR("ARSTALL_REG", 2),
    INSTITUTION_APPROVAL_STATUS("STATUS_KONTROLLERT", 3),
    PUBLICATION_INSTANCE("PUBLIKASJONSFORM", 4),
    PUBLICATION_CHANNEL("PUBLISERINGSKANAL", 5),
    PUBLICATION_CHANNEL_TYPE("PUBLISERINGSKANALTYPE", 6),
    PUBLICATION_CHANNEL_LEVEL("KVALITETSNIVAKODE", 7),
    CONTRIBUTOR_IDENTIFIER("PERSONLOPENR", 8),
    INSTITUTION_ID("INSTITUSJONSNR", 9),
    FACULTY_ID("AVDNR", 10),
    DEPARTMENT_ID("UNDAVDNR", 11),
    GROUP_ID("GRUPPENR", 12),
    CONTRIBUTOR_LAST_NAME("ETTERNAVN", 13),
    CONTRIBUTOR_FIRST_NAME("FORNAVN", 14),
    PUBLICATION_CHANNEL_NAME("PUBLISERINGSKANALNAVN", 15),
    PAGE_BEGIN("SIDE_FRA", 16),
    PAGE_END("SIDE_TIL", 17),
    PAGE_COUNT("SIDEANTALL", 18),
    PUBLICATION_TITLE("VA_TITTEL", 19),
    PUBLICATION_LANGUAGE("SPRÅK", 20),
    GLOBAL_STATUS("RAPPORTSTATUS", 21),
    PUBLICATION_CHANNEL_LEVEL_POINTS("VEKTINGSTALL", 22),
    INTERNATIONAL_COLLABORATION_FACTOR("FAKTORTALL_SAMARBEID", 23),
    CREATOR_SHARE_COUNT("FORFATTERDEL", 24),
    POINTS_FOR_AFFILIATION("FORFATTERVEKT", 25);

    private final String value;
    private final int order;

    InstitutionReportHeader(String value, int order) {
        this.value = value;
        this.order = order;
    }

    public static List<String> getOrderedValues() {
        return Arrays.stream(InstitutionReportHeader.values())
                   .sorted(Comparator.comparing(InstitutionReportHeader::getOrder))
                   .map(InstitutionReportHeader::getValue)
                   .toList();
    }

    public String getValue() {
        return value;
    }

    public int getOrder() {
        return order;
    }
}
