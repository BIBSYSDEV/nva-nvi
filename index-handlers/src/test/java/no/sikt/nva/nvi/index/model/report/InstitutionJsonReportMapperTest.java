package no.sikt.nva.nvi.index.model.report;

import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganization;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.sikt.nva.nvi.index.report.response.InstitutionJsonReport;
import no.sikt.nva.nvi.index.report.response.InstitutionSummary;
import no.sikt.nva.nvi.index.report.response.InstitutionTotals;
import no.sikt.nva.nvi.index.report.response.UndisputedCandidatesByLocalApprovalStatus;
import no.sikt.nva.nvi.index.report.response.UnitSummary;
import no.sikt.nva.nvi.index.report.response.UnitTotals;
import no.sikt.nva.nvi.report.model.Cell;
import no.sikt.nva.nvi.report.model.Row;
import no.sikt.nva.nvi.report.model.publicationpoints.PublicationPointsReportHeader;
import org.junit.jupiter.api.Test;

class InstitutionJsonReportMapperTest {

  @Test
  void shouldMapYear() {
    var year = randomString();
    var report = institutionReport(year, Sector.UHI);

    var row = PublicationPointsReportMapper.toRow(report);

    assertThat(cellValue(row, PublicationPointsReportHeader.ARSTALL)).isEqualTo(year);
  }

  @Test
  void shouldMapInstitutionId() {
    var institution = randomOrganization().build();
    var report = institutionReportWithInstitution(institution);

    var row = PublicationPointsReportMapper.toRow(report);

    assertThat(cellValue(row, PublicationPointsReportHeader.INSTITUSJON_ID))
        .isEqualTo(institution.id().toString());
  }

  @Test
  void shouldMapSector() {
    var sector = randomElement(Sector.values());
    var report = institutionReport(randomString(), sector);

    var row = PublicationPointsReportMapper.toRow(report);

    assertThat(cellValue(row, PublicationPointsReportHeader.SEKTORKODE))
        .isEqualTo(sector.toString());
  }

  @Test
  void shouldMapCandidatesApprovedByInstitutionField() {
    var approvedCount = randomInteger();
    var report = institutionReportWithApprovedCount(approvedCount);

    var row = PublicationPointsReportMapper.toRow(report);

    assertThat(cellValue(row, PublicationPointsReportHeader.GODKJENT_AV_INSTITUSJON))
        .isEqualTo(String.valueOf(approvedCount));
  }

  @Test
  void shouldMapCandidatesApprovedByAllInstitutionsField() {
    var globalApprovedCount = randomInteger();
    var report = institutionReportWithGlobalApprovedCount(globalApprovedCount);

    var row = PublicationPointsReportMapper.toRow(report);

    assertThat(cellValue(row, PublicationPointsReportHeader.GODKJENT_AV_ALLE_INSTITUSJONER))
        .isEqualTo(String.valueOf(globalApprovedCount));
  }

  @Test
  void shouldMapPointsToReportField() {
    var validPoints = randomBigDecimal();
    var report = institutionReportWithValidPoints(validPoints);

    var row = PublicationPointsReportMapper.toRow(report);

    assertThat(cellValue(row, PublicationPointsReportHeader.POENG_TIL_RAPPORTERING))
        .isEqualTo(validPoints.toPlainString());
  }

  @Test
  void shouldCreateRowsForTopLevelUnits() {
    var unit = randomUnitSummary(Collections.emptyList());
    var report = institutionReportWithUnits(List.of(unit));

    var rows = PublicationPointsReportMapper.toRows(report);

    assertThat(rows).hasSize(1);
  }

  @Test
  void shouldCreateRowsForSubUnits() {
    var subUnit = randomUnitSummary(Collections.emptyList());
    var unit = randomUnitSummary(List.of(subUnit));
    var report = institutionReportWithUnits(List.of(unit));

    var rows = PublicationPointsReportMapper.toRows(report);

    assertThat(rows).hasSize(2);
  }

  @Test
  void shouldCreateRowsForNestedSubUnits() {
    var deepSubUnit = randomUnitSummary(Collections.emptyList());
    var subUnit = randomUnitSummary(List.of(deepSubUnit));
    var unit = randomUnitSummary(List.of(subUnit));
    var report = institutionReportWithUnits(List.of(unit));

    var rows = PublicationPointsReportMapper.toRows(report);

    assertThat(rows).hasSize(3);
  }

  @Test
  void shouldMapYearWhenMappingSubUnit() {
    var year = randomString();
    var unit = randomUnitSummary(Collections.emptyList());
    var report = institutionReportWithUnitsAndYear(List.of(unit), year);

    var row = PublicationPointsReportMapper.toRows(report).getFirst();

    assertThat(cellValue(row, PublicationPointsReportHeader.ARSTALL)).isEqualTo(year);
  }

  @Test
  void shouldMapInstitutionIdWhenMappingSubUnit() {
    var unit = randomUnitSummary(Collections.emptyList());
    var report = institutionReportWithUnits(List.of(unit));

    var row = PublicationPointsReportMapper.toRows(report).getFirst();

    assertThat(cellValue(row, PublicationPointsReportHeader.INSTITUSJON_ID))
        .isEqualTo(unit.unit().id().toString());
  }

  @Test
  void shouldMapSectorWhenMappingSubUnit() {
    var sector = randomElement(Sector.values());
    var unit = randomUnitSummary(Collections.emptyList());
    var report = institutionReportWithUnitsAndSector(List.of(unit), sector);

    var row = PublicationPointsReportMapper.toRows(report).getFirst();

    assertThat(cellValue(row, PublicationPointsReportHeader.SEKTORKODE))
        .isEqualTo(sector.toString());
  }

  @Test
  void shouldMapCandidatesApprovedByInstitutionWhenMappingSubUnit() {
    var approvedCount = randomInteger();
    var unit = unitSummaryWithApprovedCount(approvedCount);
    var report = institutionReportWithUnits(List.of(unit));

    var row = PublicationPointsReportMapper.toRows(report).getFirst();

    assertThat(cellValue(row, PublicationPointsReportHeader.GODKJENT_AV_INSTITUSJON))
        .isEqualTo(String.valueOf(approvedCount));
  }

  @Test
  void shouldMapPointsToReportWhenMappingSubUnit() {
    var validPoints = randomBigDecimal();
    var unit = unitSummaryWithValidPoints(validPoints);
    var report = institutionReportWithUnits(List.of(unit));

    var row = PublicationPointsReportMapper.toRows(report).getFirst();

    assertThat(cellValue(row, PublicationPointsReportHeader.POENG_TIL_RAPPORTERING))
        .isEqualTo(validPoints.toPlainString());
  }

  private static String cellValue(Row row, PublicationPointsReportHeader header) {
    return row.cells().stream()
        .filter(cell -> header == cell.header())
        .map(Cell::string)
        .findFirst()
        .orElseThrow();
  }

  private static InstitutionJsonReport institutionReport(
      String year,
      Sector sector,
      Organization institution,
      InstitutionSummary summary,
      List<UnitSummary> units) {
    return new InstitutionJsonReport(
        randomUri(),
        new NviPeriodDto(randomUri(), year, null, null),
        sector,
        institution,
        summary,
        units);
  }

  private static InstitutionJsonReport institutionReport(String year, Sector sector) {
    return institutionReport(
        year, sector, randomOrganization().build(), randomInstitutionSummary(), List.of());
  }

  private static InstitutionJsonReport institutionReportWithInstitution(Organization institution) {
    return institutionReport(
        randomString(), Sector.UHI, institution, randomInstitutionSummary(), List.of());
  }

  private static InstitutionJsonReport institutionReportWithApprovedCount(int approvedCount) {
    var summary =
        new InstitutionSummary(
            randomInstitutionTotals(),
            new UndisputedCandidatesByLocalApprovalStatus(
                randomInteger(), randomInteger(), approvedCount, randomInteger()));
    return institutionReport(
        randomString(), Sector.UHI, randomOrganization().build(), summary, List.of());
  }

  private static InstitutionJsonReport institutionReportWithGlobalApprovedCount(
      int globalApprovedCount) {
    var summary =
        new InstitutionSummary(
            new InstitutionTotals(
                randomBigDecimal(),
                randomInteger(),
                globalApprovedCount,
                randomInteger(),
                randomInteger(),
                randomInteger()),
            randomApprovalStatus());
    return institutionReport(
        randomString(), Sector.UHI, randomOrganization().build(), summary, List.of());
  }

  private static InstitutionJsonReport institutionReportWithValidPoints(BigDecimal validPoints) {
    var summary =
        new InstitutionSummary(
            new InstitutionTotals(
                validPoints,
                randomInteger(),
                randomInteger(),
                randomInteger(),
                randomInteger(),
                randomInteger()),
            randomApprovalStatus());
    return institutionReport(
        randomString(), Sector.UHI, randomOrganization().build(), summary, List.of());
  }

  private static InstitutionJsonReport institutionReportWithUnits(List<UnitSummary> units) {
    return institutionReport(
        randomString(),
        Sector.UHI,
        randomOrganization().build(),
        randomInstitutionSummary(),
        units);
  }

  private static InstitutionJsonReport institutionReportWithUnitsAndYear(
      List<UnitSummary> units, String year) {
    return institutionReport(
        year, Sector.UHI, randomOrganization().build(), randomInstitutionSummary(), units);
  }

  private static InstitutionJsonReport institutionReportWithUnitsAndSector(
      List<UnitSummary> units, Sector sector) {
    return institutionReport(
        randomString(), sector, randomOrganization().build(), randomInstitutionSummary(), units);
  }

  private static UnitSummary randomUnitSummary(List<UnitSummary> subUnits) {
    return new UnitSummary(
        randomOrganization().build(),
        new UnitTotals(randomBigDecimal(), randomInteger(), randomInteger(), randomInteger()),
        randomApprovalStatus(),
        subUnits);
  }

  private static UnitSummary unitSummaryWithApprovedCount(int approvedCount) {
    return new UnitSummary(
        randomOrganization().build(),
        new UnitTotals(randomBigDecimal(), randomInteger(), randomInteger(), randomInteger()),
        new UndisputedCandidatesByLocalApprovalStatus(
            randomInteger(), randomInteger(), approvedCount, randomInteger()),
        List.of());
  }

  private static UnitSummary unitSummaryWithValidPoints(BigDecimal validPoints) {
    return new UnitSummary(
        randomOrganization().build(),
        new UnitTotals(validPoints, randomInteger(), randomInteger(), randomInteger()),
        randomApprovalStatus(),
        List.of());
  }

  private static InstitutionSummary randomInstitutionSummary() {
    return new InstitutionSummary(randomInstitutionTotals(), randomApprovalStatus());
  }

  private static InstitutionTotals randomInstitutionTotals() {
    return new InstitutionTotals(
        randomBigDecimal(),
        randomInteger(),
        randomInteger(),
        randomInteger(),
        randomInteger(),
        randomInteger());
  }

  private static UndisputedCandidatesByLocalApprovalStatus randomApprovalStatus() {
    return new UndisputedCandidatesByLocalApprovalStatus(
        randomInteger(), randomInteger(), randomInteger(), randomInteger());
  }
}
