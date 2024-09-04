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
    PUBLICATION_TITLE("VA_TITTEL", 15),
    GLOBAL_STATUS("RAPPORTSTATUS", 16),
    PUBLICATION_CHANNEL_LEVEL_POINTS("VEKTINGSTALL", 17),
    INTERNATIONAL_COLLABORATION_FACTOR("FAKTORTALL_SAMARBEID", 18),
    CREATOR_SHARE_COUNT("FORFATTERDEL", 19),
    POINTS_FOR_AFFILIATION("FORFATTERVEKT", 20);

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
