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
  PUBLICATION_CHANNEL_PISSN("ISSN", 7),
  PUBLICATION_CHANNEL_LEVEL("KVALITETSNIVAKODE", 8),
  CONTRIBUTOR_IDENTIFIER("PERSONLOPENR", 9),
  INSTITUTION_ID("INSTITUSJONSNR", 10),
  FACULTY_ID("AVDNR", 11),
  DEPARTMENT_ID("UNDAVDNR", 12),
  GROUP_ID("GRUPPENR", 13),
  CONTRIBUTOR_LAST_NAME("ETTERNAVN", 14),
  CONTRIBUTOR_FIRST_NAME("FORNAVN", 15),
  PUBLICATION_CHANNEL_NAME("PUBLISERINGSKANALNAVN", 16),
  PAGE_BEGIN("SIDE_FRA", 17),
  PAGE_END("SIDE_TIL", 18),
  PAGE_COUNT("SIDEANTALL", 19),
  PUBLICATION_TITLE("VA_TITTEL", 20),
  PUBLICATION_LANGUAGE("SPRÅK", 21),
  GLOBAL_STATUS("RAPPORTSTATUS", 22),
  PUBLICATION_CHANNEL_LEVEL_POINTS("VEKTINGSTALL", 23),
  INTERNATIONAL_COLLABORATION_FACTOR("FAKTORTALL_SAMARBEID", 24),
  CREATOR_SHARE_COUNT("FORFATTERDEL", 25),
  POINTS_FOR_AFFILIATION("FORFATTERVEKT", 26);

  private final String value;
  private final int order;

  InstitutionReportHeader(String value, int order) {
    this.value = value;
    this.order = order;
  }

  public static List<String> getOrderedValues() {
    return Arrays.stream(values())
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
