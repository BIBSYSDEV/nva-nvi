package no.sikt.nva.nvi.index.report.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ReportRowBuilder implements RowBuilder {

  private final List<Cell> cells;
  private final RowValidator validator;

  public ReportRowBuilder(RowValidator validator) {
    this.validator = validator;
    this.cells = new ArrayList<>();
  }

  public ReportRowBuilder withYear(String value) {
    withCell(Cell.of(ReportHeader.ARSTALL, value));
    return this;
  }

  public ReportRowBuilder withPublicationId(String value) {
    withCell(Cell.of(ReportHeader.NVAID, value));
    return this;
  }

  public ReportRowBuilder withPublicationType(String value) {
    withCell(Cell.of(ReportHeader.PUBLIKASJONSFORM, value));
    return this;
  }

  public ReportRowBuilder withPublicationChannel(String value) {
    withCell(Cell.of(ReportHeader.PUBLISERINGSKANAL, value));
    return this;
  }

  public ReportRowBuilder withPublicationChannelType(String value) {
    withCell(Cell.of(ReportHeader.PUBLISERINGSKANALTYPE, value));
    return this;
  }

  public ReportRowBuilder withPrintIssn(String value) {
    withCell(Cell.of(ReportHeader.PRINT_ISSN, value));
    return this;
  }

  public ReportRowBuilder withPublicationChannelName(String value) {
    withCell(Cell.of(ReportHeader.PUBLISERINGSKANALNAVN, value));
    return this;
  }

  public ReportRowBuilder withScientificValue(String value) {
    withCell(Cell.of(ReportHeader.KVALITETSNIVAKODE, value));
    return this;
  }

  public ReportRowBuilder withContributorId(String value) {
    withCell(Cell.of(ReportHeader.PERSONLOPENR, value));
    return this;
  }

  public ReportRowBuilder withAffiliationIdentifier(String value) {
    withCell(Cell.of(ReportHeader.INSTITUSJON, value));
    return this;
  }

  public ReportRowBuilder withAffiliationId(String value) {
    withCell(Cell.of(ReportHeader.INSTITUSJON_ID, value));
    return this;
  }

  public ReportRowBuilder withHkdirInstitutionCode(String value) {
    withCell(Cell.of(ReportHeader.HKDIR_INSTITUSJONSKODE, value));
    return this;
  }

  public ReportRowBuilder withInstitutionNumber(String value) {
    withCell(Cell.of(ReportHeader.INSTITUSJONSNR, value));
    return this;
  }

  public ReportRowBuilder withFacultyNumber(String value) {
    withCell(Cell.of(ReportHeader.AVDNR, value));
    return this;
  }

  public ReportRowBuilder withDepartmentNumber(String value) {
    withCell(Cell.of(ReportHeader.UNDAVDNR, value));
    return this;
  }

  public ReportRowBuilder withGroupNumber(String value) {
    withCell(Cell.of(ReportHeader.GRUPPENR, value));
    return this;
  }

  public ReportRowBuilder withLastName(String value) {
    withCell(Cell.of(ReportHeader.ETTERNAVN, value));
    return this;
  }

  public ReportRowBuilder withFirstName(String value) {
    withCell(Cell.of(ReportHeader.FORNAVN, value));
    return this;
  }

  public ReportRowBuilder withTitle(String value) {
    withCell(Cell.of(ReportHeader.TITTEL, value));
    return this;
  }

  public ReportRowBuilder withApprovalStatus(String value) {
    withCell(Cell.of(ReportHeader.STATUS_KONTROLLERT, value));
    return this;
  }

  public ReportRowBuilder withGlobalStatus(String value) {
    withCell(Cell.of(ReportHeader.RAPPORTSTATUS, value));
    return this;
  }

  public ReportRowBuilder withInternationalCollaborationFactor(String value) {
    withCell(Cell.of(ReportHeader.FAKTORTALL_SAMARBEID, value));
    return this;
  }

  public ReportRowBuilder withPublicationTypeChannelLevelPoints(BigDecimal value) {
    withCell(Cell.of(ReportHeader.VEKTINGSTALL, value));
    return this;
  }

  public ReportRowBuilder withCreatorShareCount(BigDecimal value) {
    withCell(Cell.of(ReportHeader.FORFATTERDEL, value));
    return this;
  }

  public ReportRowBuilder withTentativePublishingPoints(BigDecimal value) {
    withCell(Cell.of(ReportHeader.TENTATIVE_PUBLISERINGSPOENG, value));
    return this;
  }

  public ReportRowBuilder withPublishingPoints(BigDecimal value) {
    withCell(Cell.of(ReportHeader.PUBLISERINGSPOENG, value));
    return this;
  }

  @Override
  public List<Cell> cells() {
    return cells;
  }

  @Override
  public ReportRow build() {
    var row = new ReportRow(sortCells());
    validator.validate(row);
    return row;
  }

  private void withCell(Cell cell) {
    cells.add(cell);
  }

  private List<Cell> sortCells() {
    return cells().stream()
        .sorted(Comparator.comparingInt(cell -> cell.header().ordinal()))
        .toList();
  }
}
