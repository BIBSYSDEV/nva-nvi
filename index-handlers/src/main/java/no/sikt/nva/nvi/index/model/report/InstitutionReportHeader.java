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
    CONTRIBUTOR_IDENTIFIER("PERSONLOPENR", 5),
    INSTITUTION_ID("INSTITUSJONSNR", 6),
    FACULTY_ID("AVDNR", 7),
    DEPARTMENT_ID("UNDAVDNR", 8),
    GROUP_ID("GRUPPENR", 9),
    CONTRIBUTOR_LAST_NAME("ETTERNAVN", 10),
    CONTRIBUTOR_FIRST_NAME("FORNAVN", 11),
    PUBLICATION_TITLE("VA_TITTEL", 12),
    GLOBAL_STATUS("RAPPORTSTATUS", 13),
    PUBLICATION_CHANNEL_LEVEL_POINTS("VEKTINGSTALL", 14),
    INTERNATIONAL_COLLABORATION_FACTOR("FAKTORTALL_SAMARBEID", 15),
    CREATOR_SHARE_COUNT("FORFATTERDEL", 16),
    POINTS_FOR_AFFILIATION("FORFATTERVEKT", 17);

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
