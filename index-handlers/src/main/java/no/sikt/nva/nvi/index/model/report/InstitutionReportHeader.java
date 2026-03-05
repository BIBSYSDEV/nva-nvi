package no.sikt.nva.nvi.index.model.report;

import static no.sikt.nva.nvi.index.model.report.InstitutionReportConstants.CELL_TYPE_NUMERIC;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportConstants.CELL_TYPE_STRING;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public enum InstitutionReportHeader {
  REPORTING_YEAR("ARSTALL", CELL_TYPE_STRING, 0),
  PUBLICATION_IDENTIFIER("NVAID", CELL_TYPE_STRING, 1),
  PUBLISHED_YEAR("ARSTALL_REG", CELL_TYPE_STRING, 2),
  INSTITUTION_APPROVAL_STATUS("STATUS_KONTROLLERT", CELL_TYPE_STRING, 3),
  PUBLICATION_INSTANCE("PUBLIKASJONSFORM", CELL_TYPE_STRING, 4),
  PUBLICATION_CHANNEL("PUBLISERINGSKANAL", CELL_TYPE_STRING, 5),
  PUBLICATION_CHANNEL_TYPE("PUBLISERINGSKANALTYPE", CELL_TYPE_STRING, 6),
  PUBLICATION_CHANNEL_PISSN("ISSN", CELL_TYPE_STRING, 7),
  PUBLICATION_CHANNEL_LEVEL("KVALITETSNIVAKODE", CELL_TYPE_STRING, 8),
  CONTRIBUTOR_IDENTIFIER("PERSONLOPENR", CELL_TYPE_STRING, 9),
  INSTITUTION_ID("INSTITUSJONSNR", CELL_TYPE_STRING, 10),
  FACULTY_ID("AVDNR", CELL_TYPE_STRING, 11),
  DEPARTMENT_ID("UNDAVDNR", CELL_TYPE_STRING, 12),
  GROUP_ID("GRUPPENR", CELL_TYPE_STRING, 13),
  CONTRIBUTOR_LAST_NAME("ETTERNAVN", CELL_TYPE_STRING, 14),
  CONTRIBUTOR_FIRST_NAME("FORNAVN", CELL_TYPE_STRING, 15),
  PUBLICATION_CHANNEL_NAME("PUBLISERINGSKANALNAVN", CELL_TYPE_STRING, 16),
  PAGE_BEGIN("SIDE_FRA", CELL_TYPE_STRING, 17),
  PAGE_END("SIDE_TIL", CELL_TYPE_STRING, 18),
  PAGE_COUNT("SIDEANTALL", CELL_TYPE_STRING, 19),
  PUBLICATION_TITLE("VA_TITTEL", CELL_TYPE_STRING, 20),
  PUBLICATION_LANGUAGE("SPRÅK", CELL_TYPE_STRING, 21),
  GLOBAL_STATUS("RAPPORTSTATUS", CELL_TYPE_STRING, 22),
  PUBLICATION_CHANNEL_LEVEL_POINTS("VEKTINGSTALL", CELL_TYPE_NUMERIC, 23),
  INTERNATIONAL_COLLABORATION_FACTOR("FAKTORTALL_SAMARBEID", CELL_TYPE_NUMERIC, 24),
  CREATOR_SHARE_COUNT("FORFATTERDEL", CELL_TYPE_NUMERIC, 25),
  POINTS_FOR_AFFILIATION("FORFATTERVEKT", CELL_TYPE_NUMERIC, 26);

  private final String value;
  private final int order;
  private final String cellType;

  InstitutionReportHeader(String value, String cellType, int order) {
    this.value = value;
    this.order = order;
    this.cellType = cellType;
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

  public String getCellType() {
    return cellType;
  }

  public int getOrder() {
    return order;
  }
}
