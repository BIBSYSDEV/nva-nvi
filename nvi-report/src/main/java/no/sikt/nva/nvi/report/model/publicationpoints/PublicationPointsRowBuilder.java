package no.sikt.nva.nvi.report.model.publicationpoints;

import java.math.BigDecimal;
import no.sikt.nva.nvi.report.model.Cell;
import no.sikt.nva.nvi.report.model.RowBuilder;
import no.sikt.nva.nvi.report.model.authorshares.DefaultValidator;

public class PublicationPointsRowBuilder extends RowBuilder {

  public PublicationPointsRowBuilder() {
    super();
  }

  @Override
  protected void validate() {
    DefaultValidator.create().validate(this::cells);
  }

  public PublicationPointsRowBuilder withYear(String value) {
    withCell(Cell.of(PublicationPointsReportHeader.ARSTALL, value));
    return this;
  }

  public PublicationPointsRowBuilder withInstitutionId(String value) {
    withCell(Cell.of(PublicationPointsReportHeader.INSTITUSJON_ID, value));
    return this;
  }

  public PublicationPointsRowBuilder withSector(String value) {
    withCell(Cell.of(PublicationPointsReportHeader.SEKTORKODE, value));
    return this;
  }

  public PublicationPointsRowBuilder withCandidatesNumberApprovedByInstitution(String value) {
    withCell(Cell.of(PublicationPointsReportHeader.GODKJENT_AV_INSTITUSJON, value));
    return this;
  }

  public PublicationPointsRowBuilder withCandidatesNumberApprovedByAllInstitutions(String value) {
    withCell(Cell.of(PublicationPointsReportHeader.GODKJENT_AV_ALLE_INSTITUSJONER, value));
    return this;
  }

  public PublicationPointsRowBuilder withPointsToReports(BigDecimal value) {
    withCell(Cell.of(PublicationPointsReportHeader.POENG_TIL_RAPPORTERING, value));
    return this;
  }
}
