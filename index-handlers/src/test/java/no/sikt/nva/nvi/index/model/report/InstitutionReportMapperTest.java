package no.sikt.nva.nvi.index.model.report;

import static no.sikt.nva.nvi.common.model.OrganizationFixtures.organizationIdFromIdentifier;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomPages;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomPublicationChannel;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.ARSTALL;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.AVDNR;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.DBH_AVDELINGSKODE;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.DBH_FAKULTETSKODE;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.DBH_INSTITUSJONSKODE;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.ETTERNAVN;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.FAKTORTALL_SAMARBEID;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.FORFATTERDEL;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.FORNAVN;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.GRUPPENR;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.INSTITUSJON;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.INSTITUSJONSNR;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.INSTITUSJON_ID;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.KVALITETSNIVAKODE;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.NVAID;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.PERSONLOPENR;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.PRINT_ISSN;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.PUBLIKASJONSFORM;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.PUBLISERINGSKANAL;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.PUBLISERINGSKANALNAVN;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.PUBLISERINGSKANALTYPE;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.PUBLISERINGSPOENG;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.RAPPORTSTATUS;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.SEKTORKODE;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.STATUS_KONTROLLERT;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.STATUS_RBO;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.TENTATIVE_PUBLISERINGSPOENG;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.TITTEL;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.UNDAVDNR;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.VEKTINGSTALL;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView.CreatorAffiliationPointsView;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import no.sikt.nva.nvi.report.model.Cell;
import no.sikt.nva.nvi.report.model.Header;
import no.sikt.nva.nvi.report.model.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("PMD.GodClass")
class InstitutionReportMapperTest {

  private static final URI INSTITUTION_ID =
      URI.create("https://example.org/cristin/organization/203.0.0.0");
  private static final String ORGANIZATION_PRESENT_IN_DBH = "203.14.5.0";

  @Test
  void shouldHandleMissingInstitutionFromUHInstitutions() {
    var document = randomDocument("1.1.1.1");
    var row = toRow(document);
    assertThat(cellValue(row, DBH_INSTITUSJONSKODE)).isEqualTo(EMPTY_STRING);
    assertThat(cellValue(row, DBH_FAKULTETSKODE)).isEqualTo(EMPTY_STRING);
  }

  @Test
  void shouldMapInstitutionIdentifierToDbhInstitutionIdentifier() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, DBH_INSTITUSJONSKODE)).isEqualTo("238");
  }

  @Test
  void shouldMapInstitutionIdentifierToDbhFacultyIdentifier() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, DBH_FAKULTETSKODE)).isEqualTo("250");
  }

  @Test
  void shouldMapInstitutionIdentifierToDbhDepartmentIdentifier() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, DBH_AVDELINGSKODE)).isEqualTo("250350");
  }

  @Test
  void
      shouldMapInstitutionIdentifierToTopLeveDbhEntryMatchingTopLevelIdentifierWhenNoMatchForFullIdentifier() {
    var document = randomDocument("203.a.b.c");
    var row = toRow(document);

    assertThat(cellValue(row, DBH_INSTITUSJONSKODE)).isNotEmpty();
    assertThat(cellValue(row, DBH_FAKULTETSKODE)).isEmpty();
    assertThat(cellValue(row, DBH_AVDELINGSKODE)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldMapApprovalRboInstitutionToRboStatus(boolean rboInstitution) {
    var document = randomDocumentWithApprovalRboInstitution(rboInstitution);
    var row = toRow(document);
    assertThat(cellValue(row, STATUS_RBO)).isEqualTo(rboInstitution ? "J" : "N");
  }

  @Test
  void shouldMapApprovalSectorToSector() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var sector = document.approvals().getFirst().sector();
    var row = toRow(document);
    assertThat(cellValue(row, SEKTORKODE)).isEqualTo(sector);
  }

  @Test
  void shouldReturnEmptyStringForDbhInstitutionCodeWhenNoPresent() {
    var document = randomDocument("1.1.1.1");
    var row = toRow(document);
    assertThat(cellValue(row, DBH_INSTITUSJONSKODE)).isEmpty();
  }

  @Test
  void shouldMapYear() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, ARSTALL)).isEqualTo(document.reportingPeriod().year());
  }

  @Test
  void shouldMapPublicationId() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, NVAID)).isEqualTo(document.publicationDetails().id());
  }

  @Test
  void shouldMapPublicationType() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, PUBLIKASJONSFORM)).isEqualTo(document.publicationDetails().type());
  }

  @Test
  void shouldMapPublicationChannel() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, PUBLISERINGSKANAL))
        .isEqualTo(document.publicationDetails().publicationChannel().id().toString());
  }

  @Test
  void shouldMapPublicationChannelType() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, PUBLISERINGSKANALTYPE))
        .isEqualTo(document.publicationDetails().publicationChannel().type());
  }

  @Test
  void shouldMapPrintIssn() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, PRINT_ISSN))
        .isEqualTo(document.publicationDetails().publicationChannel().printIssn());
  }

  @Test
  void shouldMapPublicationChannelName() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, PUBLISERINGSKANALNAVN))
        .isEqualTo(document.publicationDetails().publicationChannel().name());
  }

  @Test
  void shouldMapScientificValue() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, KVALITETSNIVAKODE))
        .isEqualTo(document.publicationDetails().publicationChannel().scientificValue().getValue());
  }

  @Test
  void shouldMapContributorId() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, PERSONLOPENR)).isEqualTo(firstContributor(document).id());
  }

  @Test
  void shouldMapAffiliationIdentifier() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, INSTITUSJON)).isEqualTo(firstAffiliation(document).identifier());
  }

  @Test
  void shouldMapAffiliationId() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, INSTITUSJON_ID))
        .isEqualTo(firstAffiliation(document).id().toString());
  }

  @Test
  void shouldMapInstitutionNumber() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, INSTITUSJONSNR))
        .isEqualTo(firstAffiliation(document).getInstitutionIdentifier());
  }

  @Test
  void shouldMapFacultyNumber() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, AVDNR)).isEqualTo(firstAffiliation(document).getFacultyIdentifier());
  }

  @Test
  void shouldMapDepartmentNumber() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, UNDAVDNR))
        .isEqualTo(firstAffiliation(document).getDepartmentIdentifier());
  }

  @Test
  void shouldMapGroupNumber() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, GRUPPENR)).isEqualTo(firstAffiliation(document).getGroupIdentifier());
  }

  @Test
  void shouldMapLastName() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, ETTERNAVN)).isEqualTo(firstContributor(document).name());
  }

  @Test
  void shouldMapFirstName() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, FORNAVN)).isEqualTo(firstContributor(document).name());
  }

  @Test
  void shouldMapTitle() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, TITTEL)).isEqualTo(document.publicationDetails().title());
  }

  @ParameterizedTest
  @CsvSource({"APPROVED, J", "REJECTED, N", "PENDING, ?", "NEW, ?"})
  void shouldMapApprovalStatus(ApprovalStatus status, String expected) {
    var document = randomDocumentWithApprovalStatus(status);
    var row = toRow(document);
    assertThat(cellValue(row, STATUS_KONTROLLERT)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"APPROVED, J", "REJECTED, N", "PENDING, ?", "DISPUTE, T"})
  void shouldMapGlobalApprovalStatus(GlobalApprovalStatus status, String expected) {
    var document = randomDocumentWithGlobalStatus(status);
    var row = toRow(document);
    assertThat(cellValue(row, RAPPORTSTATUS)).isEqualTo(expected);
  }

  @Test
  void shouldMapInternationalCollaborationFactor() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, FAKTORTALL_SAMARBEID))
        .isEqualTo(String.valueOf(document.internationalCollaborationFactor()));
  }

  @Test
  void shouldMapInternationalCollaborationFactorAsNaWhenNull() {
    var document = randomDocumentWithNullCollaborationFactor();
    var row = toRow(document);
    assertThat(cellValue(row, FAKTORTALL_SAMARBEID)).isEqualTo("N/A");
  }

  @Test
  void shouldMapPublicationTypeChannelLevelPoints() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, VEKTINGSTALL))
        .isEqualTo(document.publicationTypeChannelLevelPoints().toPlainString());
  }

  @Test
  void shouldMapCreatorShareCount() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, FORFATTERDEL))
        .isEqualTo(BigDecimal.valueOf(document.creatorShareCount()).toPlainString());
  }

  @Test
  void shouldMapTentativePublishingPoints() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, TENTATIVE_PUBLISERINGSPOENG))
        .isEqualTo(firstAffiliationPoints(document).toPlainString());
  }

  @Test
  void shouldMapPublishingPointsEqualToTentativeWhenApproved() {
    var document = randomDocument(ORGANIZATION_PRESENT_IN_DBH);
    var row = toRow(document);
    assertThat(cellValue(row, PUBLISERINGSPOENG))
        .isEqualTo(firstAffiliationPoints(document).toPlainString());
  }

  @Test
  void shouldMapPublishingPointsToZeroWhenNotApproved() {
    var document = randomDocumentWithGlobalStatus(GlobalApprovalStatus.PENDING);
    var row = toRow(document);
    assertThat(cellValue(row, PUBLISERINGSPOENG)).isEqualTo("0");
  }

  @Test
  void shouldReturnEmptyStreamWhenNoApprovalExistsForInstitution() {
    var otherInstitutionId = URI.create("https://example.org/organization/999");
    assertThat(
            InstitutionReportMapper.mapToReportRows(
                randomDocument(ORGANIZATION_PRESENT_IN_DBH), otherInstitutionId))
        .isEmpty();
  }

  @Test
  void shouldProduceOneRowPerContributorWhenOneAffiliationAndTwoContributors() {
    var contributorId1 = randomUri();
    var contributorId2 = randomUri();
    var affiliationId = randomOrganizationId();

    var document =
        reportDocument(
            GlobalApprovalStatus.APPROVED,
            randomBigDecimal(),
            randomInteger(),
            randomPublicationDetails(
                List.of(
                    randomContributor(contributorId1, affiliationId),
                    randomContributor(contributorId2, affiliationId))),
            List.of(
                newApproval(
                    ApprovalStatus.APPROVED,
                    List.of(
                        newCreatorAffiliationPoints(contributorId1, affiliationId),
                        newCreatorAffiliationPoints(contributorId2, affiliationId)),
                    randomBoolean())));

    var rows = InstitutionReportMapper.mapToReportRows(document, INSTITUTION_ID).toList();

    assertThat(rows).hasSize(2);
    assertThat(rows)
        .extracting(row -> cellValue(row, PERSONLOPENR))
        .containsExactlyInAnyOrder(contributorId1.toString(), contributorId2.toString());
  }

  @Test
  void shouldProduceOneRowPerAffiliationWhenOneContributorAndTwoAffiliations() {
    var contributorId = randomUri();
    var affiliationId1 = randomOrganizationId();
    var affiliationId2 = randomOrganizationId();

    var document =
        reportDocument(
            GlobalApprovalStatus.APPROVED,
            randomBigDecimal(),
            randomInteger(),
            randomPublicationDetails(
                List.of(randomContributor(contributorId, affiliationId1, affiliationId2))),
            List.of(
                newApproval(
                    ApprovalStatus.APPROVED,
                    List.of(
                        newCreatorAffiliationPoints(contributorId, affiliationId1),
                        newCreatorAffiliationPoints(contributorId, affiliationId2)),
                    randomBoolean())));

    var rows = InstitutionReportMapper.mapToReportRows(document, INSTITUTION_ID).toList();

    assertThat(rows).hasSize(2);
    assertThat(rows)
        .extracting(row -> cellValue(row, INSTITUSJON_ID))
        .containsExactlyInAnyOrder(affiliationId1.toString(), affiliationId2.toString());
  }

  private static String cellValue(Row row, Header header) {
    return row.cells().stream()
        .filter(cell -> cell.header().equals(header))
        .findFirst()
        .map(Cell::string)
        .orElseThrow(() -> new AssertionError("No cell for header: " + header));
  }

  private Row toRow(ReportDocument document) {
    return InstitutionReportMapper.mapToReportRows(document, INSTITUTION_ID)
        .findFirst()
        .orElseThrow();
  }

  private static NviContributor firstContributor(ReportDocument document) {
    return document.publicationDetails().nviContributors().getFirst();
  }

  private static NviOrganization firstAffiliation(ReportDocument document) {
    return firstContributor(document)
        .getAffiliationsPartOfOrEqualTo(INSTITUTION_ID)
        .findFirst()
        .orElseThrow();
  }

  private static BigDecimal firstAffiliationPoints(ReportDocument document) {
    return InstitutionReportMapper.getPointsForAffiliation(
        document.approvals().getFirst(), firstContributor(document), firstAffiliation(document));
  }

  private ReportDocument randomDocument(String organizationIdentifier) {
    var contributorId = randomUri();
    var affiliationId = organizationIdFromIdentifier(organizationIdentifier);
    var points = randomBigDecimal();
    return reportDocument(
        GlobalApprovalStatus.APPROVED,
        randomBigDecimal(),
        randomInteger(),
        randomPublicationDetails(List.of(randomContributor(contributorId, affiliationId))),
        List.of(
            newApproval(
                ApprovalStatus.APPROVED,
                List.of(newCreatorAffiliationPoints(contributorId, affiliationId, points)),
                randomBoolean())));
  }

  private ReportDocument randomDocumentWithApprovalStatus(ApprovalStatus approvalStatus) {
    var contributorId = randomUri();
    var affiliationId = randomOrganizationId();
    return reportDocument(
        GlobalApprovalStatus.APPROVED,
        randomBigDecimal(),
        randomInteger(),
        randomPublicationDetails(List.of(randomContributor(contributorId, affiliationId))),
        List.of(
            newApproval(
                approvalStatus,
                List.of(newCreatorAffiliationPoints(contributorId, affiliationId)),
                randomBoolean())));
  }

  private ReportDocument randomDocumentWithGlobalStatus(GlobalApprovalStatus globalStatus) {
    var contributorId = randomUri();
    var affiliationId = randomOrganizationId();
    return reportDocument(
        globalStatus,
        randomBigDecimal(),
        randomInteger(),
        randomPublicationDetails(List.of(randomContributor(contributorId, affiliationId))),
        List.of(
            newApproval(
                ApprovalStatus.APPROVED,
                List.of(newCreatorAffiliationPoints(contributorId, affiliationId)),
                randomBoolean())));
  }

  private ReportDocument randomDocumentWithNullCollaborationFactor() {
    return randomDocumentWithApprovalRboInstitution(randomBoolean());
  }

  private ReportDocument randomDocumentWithApprovalRboInstitution(boolean rboInstitution) {
    var contributorId = randomUri();
    var affiliationId = randomOrganizationId();
    return new ReportDocument(
        UUID.randomUUID(),
        new ReportingPeriod(randomYear()),
        GlobalApprovalStatus.APPROVED,
        randomBigDecimal(),
        null,
        randomInteger(),
        randomPublicationDetails(List.of(randomContributor(contributorId, affiliationId))),
        List.of(
            newApproval(
                ApprovalStatus.APPROVED,
                List.of(newCreatorAffiliationPoints(contributorId, affiliationId)),
                rboInstitution)));
  }

  private static ReportDocument reportDocument(
      GlobalApprovalStatus globalStatus,
      BigDecimal channelLevelPoints,
      int creatorShareCount,
      ReportPublicationDetails publicationDetails,
      List<ReportApproval> approvals) {
    return new ReportDocument(
        UUID.randomUUID(),
        new ReportingPeriod(randomYear()),
        globalStatus,
        channelLevelPoints,
        randomBigDecimal(),
        creatorShareCount,
        publicationDetails,
        approvals);
  }

  private static ReportPublicationDetails randomPublicationDetails(
      List<NviContributor> contributors) {
    return new ReportPublicationDetails(
        randomUri().toString(),
        randomString(),
        randomString(),
        new PublicationDateDto(randomYear(), null, null),
        contributors,
        randomPublicationChannel(),
        randomPages(),
        null);
  }

  private static NviContributor randomContributor(URI contributorId, URI affiliationId) {
    return NviContributor.builder()
        .withId(contributorId.toString())
        .withName(randomString())
        .withAffiliations(
            List.of(
                NviOrganization.builder()
                    .withId(affiliationId)
                    .withPartOf(List.of(INSTITUTION_ID))
                    .build()))
        .build();
  }

  private static NviContributor randomContributor(
      URI contributorId, URI affiliationId1, URI affiliationId2) {
    return NviContributor.builder()
        .withId(contributorId.toString())
        .withName(randomString())
        .withAffiliations(
            List.of(
                NviOrganization.builder()
                    .withId(affiliationId1)
                    .withPartOf(List.of(INSTITUTION_ID))
                    .build(),
                NviOrganization.builder()
                    .withId(affiliationId2)
                    .withPartOf(List.of(INSTITUTION_ID))
                    .build()))
        .build();
  }

  private static ReportApproval newApproval(
      ApprovalStatus approvalStatus,
      List<CreatorAffiliationPointsView> points,
      boolean rboInstitution) {
    var total =
        points.stream()
            .map(CreatorAffiliationPointsView::points)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new ReportApproval(
        INSTITUTION_ID,
        approvalStatus,
        InstitutionPointsView.builder()
            .withInstitutionId(INSTITUTION_ID)
            .withInstitutionPoints(total)
            .withCreatorAffiliationPoints(points)
            .build(),
        randomString(),
        rboInstitution);
  }

  private static CreatorAffiliationPointsView newCreatorAffiliationPoints(
      URI contributorId, URI affiliationId) {
    return newCreatorAffiliationPoints(contributorId, affiliationId, randomBigDecimal());
  }

  private static CreatorAffiliationPointsView newCreatorAffiliationPoints(
      URI contributorId, URI affiliationId, BigDecimal points) {
    return CreatorAffiliationPointsView.builder()
        .withNviCreator(contributorId)
        .withAffiliationId(affiliationId)
        .withPoints(points)
        .build();
  }
}
