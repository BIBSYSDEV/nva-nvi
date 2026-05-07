package no.sikt.nva.nvi.index.model.report;

import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.sikt.nva.nvi.index.report.response.InstitutionJsonReport;
import no.sikt.nva.nvi.index.report.response.UnitSummary;
import no.sikt.nva.nvi.report.model.Row;
import no.sikt.nva.nvi.report.model.publicationpoints.PublicationPointsRowBuilder;

public final class PublicationPointsReportMapper {

  private PublicationPointsReportMapper() {}

  public static List<Row> toRows(InstitutionJsonReport report) {
    return report.units().stream()
        .flatMap(PublicationPointsReportMapper::extractAllSubUnits)
        .map(unit -> unitToRow(unit, report.period(), report.sector()))
        .toList();
  }

  public static Row toRow(InstitutionJsonReport report) {
    return new PublicationPointsRowBuilder()
        .withYear(report.period().publishingYear())
        .withInstitutionId(report.institution().id().toString())
        .withSector(report.sector().toString())
        .withCandidatesNumberApprovedByInstitution(
            String.valueOf(report.institutionSummary().byLocalApprovalStatus().approvedCount()))
        .withCandidatesNumberApprovedByAllInstitutions(
            String.valueOf(report.institutionSummary().totals().globalApprovedCount()))
        .withPointsToReports(report.institutionSummary().totals().validPoints())
        .build();
  }

  private static Stream<UnitSummary> extractAllSubUnits(UnitSummary unit) {
    return Stream.concat(
        Stream.of(unit),
        unit.units().stream().flatMap(PublicationPointsReportMapper::extractAllSubUnits));
  }

  private static Row unitToRow(UnitSummary unit, NviPeriodDto period, Sector sector) {
    return new PublicationPointsRowBuilder()
        .withYear(period.publishingYear())
        .withInstitutionId(unit.unit().id().toString())
        .withSector(sector.toString())
        .withCandidatesNumberApprovedByInstitution(
            String.valueOf(unit.byLocalApprovalStatus().approvedCount()))
        .withCandidatesNumberApprovedByAllInstitutions("?")
        .withPointsToReports(unit.totals().validPoints())
        .build();
  }
}
