package no.sikt.nva.nvi.index.model.report;

import static no.sikt.nva.nvi.index.report.model.Header.ARSTALL;
import static no.sikt.nva.nvi.index.report.model.Header.AVDNR;
import static no.sikt.nva.nvi.index.report.model.Header.FAKTORTALL_SAMARBEID;
import static no.sikt.nva.nvi.index.report.model.Header.GRUPPENR;
import static no.sikt.nva.nvi.index.report.model.Header.INSTITUSJONSNR;
import static no.sikt.nva.nvi.index.report.model.Header.KVALITETSNIVAKODE;
import static no.sikt.nva.nvi.index.report.model.Header.PERSONLOPENR;
import static no.sikt.nva.nvi.index.report.model.Header.PRINT_ISSN;
import static no.sikt.nva.nvi.index.report.model.Header.PUBLISERINGSKANAL;
import static no.sikt.nva.nvi.index.report.model.Header.PUBLISERINGSKANALNAVN;
import static no.sikt.nva.nvi.index.report.model.Header.PUBLISERINGSKANALTYPE;
import static no.sikt.nva.nvi.index.report.model.Header.RAPPORTSTATUS;
import static no.sikt.nva.nvi.index.report.model.Header.STATUS_KONTROLLERT;
import static no.sikt.nva.nvi.index.report.model.Header.TENTATIVE_PUBLISERINGSPOENG;
import static no.sikt.nva.nvi.index.report.model.Header.UNDAVDNR;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView.CreatorAffiliationPointsView;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.Pages;
import no.sikt.nva.nvi.index.model.document.PublicationChannel;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import no.sikt.nva.nvi.index.report.model.Cell;
import no.sikt.nva.nvi.index.report.model.Header;
import no.sikt.nva.nvi.index.report.model.Row;
import org.junit.jupiter.api.Test;

class InstitutionReportMapperTest {

  private static final URI INSTITUTION_ID = URI.create("https://example.org/organization/185");
  private static final URI AFFILIATION_ID =
      URI.create("https://example.org/organization/185.15.10.5");
  private static final URI CONTRIBUTOR_ID = URI.create("https://example.org/person/abc-123");
  private static final String REPORTING_YEAR = "2024";
  private static final String PUBLICATION_YEAR = "2023";
  private static final String PUBLICATION_ID_PREFIX = "https://example.org/publications/";
  private static final String PUBLICATION_TYPE = "AcademicArticle";
  private static final BigDecimal INTERNATIONAL_COLLABORATION_FACTOR = new BigDecimal("1.3");

  @Test
  void shouldProduceSingleRowForSingleContributorWithSingleMatchingAffiliation() {
    var points = new BigDecimal("0.7500");
    var document = documentWithContributorAndApproval(points);

    var rows = InstitutionReportMapper.mapToReportRows(document, INSTITUTION_ID).toList();

    assertThat(rows).hasSize(1);
    var row = rows.getFirst();
    assertThat(cellValue(row, ARSTALL)).isEqualTo(REPORTING_YEAR);
    assertThat(cellValue(row, STATUS_KONTROLLERT)).isEqualTo("J");
    assertThat(cellValue(row, RAPPORTSTATUS)).isEqualTo("J");
    assertThat(cellValue(row, PERSONLOPENR)).isEqualTo(CONTRIBUTOR_ID.toString());
    assertThat(cellValue(row, TENTATIVE_PUBLISERINGSPOENG)).isEqualTo(points.toPlainString());
  }

  @Test
  void shouldPopulateOrganizationIdentifiersFromAffiliation() {
    var document = documentWithContributorAndApproval(BigDecimal.ONE);

    var row =
        InstitutionReportMapper.mapToReportRows(document, INSTITUTION_ID).findFirst().orElseThrow();

    assertThat(cellValue(row, INSTITUSJONSNR)).isEqualTo("185");
    assertThat(cellValue(row, AVDNR)).isEqualTo("15");
    assertThat(cellValue(row, UNDAVDNR)).isEqualTo("10");
    assertThat(cellValue(row, GRUPPENR)).isEqualTo("5");
  }

  @Test
  void shouldPopulatePublicationChannelFields() {
    var channelId = URI.create("https://example.org/channel/xyz");
    var channel =
        PublicationChannel.builder()
            .withId(channelId)
            .withType("Journal")
            .withScientificValue(ScientificValue.LEVEL_ONE)
            .withName("Some Journal")
            .withPrintIssn("1234-5678")
            .build();
    var document = documentWithChannel(channel);

    var row =
        InstitutionReportMapper.mapToReportRows(document, INSTITUTION_ID).findFirst().orElseThrow();

    assertThat(cellValue(row, PUBLISERINGSKANAL)).isEqualTo(channelId.toString());
    assertThat(cellValue(row, PUBLISERINGSKANALTYPE)).isEqualTo("Journal");
    assertThat(cellValue(row, PRINT_ISSN)).isEqualTo("1234-5678");
    assertThat(cellValue(row, KVALITETSNIVAKODE)).isEqualTo(ScientificValue.LEVEL_ONE.getValue());
    assertThat(cellValue(row, PUBLISERINGSKANALNAVN)).isEqualTo("Some Journal");
  }

  @Test
  void shouldReturnEmptyStreamWhenNoApprovalExistsForInstitution() {
    var otherInstitutionId = URI.create("https://example.org/organization/999");
    var document = documentWithContributorAndApproval(BigDecimal.ONE);

    var rows = InstitutionReportMapper.mapToReportRows(document, otherInstitutionId).toList();

    assertThat(rows).isEmpty();
  }

  @Test
  void shouldProduceOneRowPerContributorAffiliationPair() {
    var secondContributorId = URI.create("https://example.org/person/def-456");
    var secondAffiliationId = URI.create("https://example.org/organization/185.15.10.6");
    var points1 = new BigDecimal("0.3000");
    var points2 = new BigDecimal("0.4000");

    var affiliation1 =
        NviOrganization.builder()
            .withId(AFFILIATION_ID)
            .withPartOf(List.of(INSTITUTION_ID))
            .build();
    var affiliation2 =
        NviOrganization.builder()
            .withId(secondAffiliationId)
            .withPartOf(List.of(INSTITUTION_ID))
            .build();

    var contributor1 =
        NviContributor.builder()
            .withId(CONTRIBUTOR_ID.toString())
            .withName("Doe, John")
            .withAffiliations(List.of(affiliation1))
            .build();
    var contributor2 =
        NviContributor.builder()
            .withId(secondContributorId.toString())
            .withName("Smith, Jane")
            .withAffiliations(List.of(affiliation2))
            .build();

    var creatorAffiliationPointsView1 =
        CreatorAffiliationPointsView.builder()
            .withNviCreator(CONTRIBUTOR_ID)
            .withAffiliationId(AFFILIATION_ID)
            .withPoints(points1)
            .build();
    var creatorAffiliationPointsView2 =
        CreatorAffiliationPointsView.builder()
            .withNviCreator(secondContributorId)
            .withAffiliationId(secondAffiliationId)
            .withPoints(points2)
            .build();
    var institutionPoints =
        InstitutionPointsView.builder()
            .withInstitutionId(INSTITUTION_ID)
            .withInstitutionPoints(new BigDecimal("0.7000"))
            .withCreatorAffiliationPoints(
                List.of(creatorAffiliationPointsView1, creatorAffiliationPointsView2))
            .build();

    var approval = new ReportApproval(INSTITUTION_ID, ApprovalStatus.APPROVED, institutionPoints);
    var publicationDetails =
        new ReportPublicationDetails(
            PUBLICATION_ID_PREFIX + UUID.randomUUID(),
            PUBLICATION_TYPE,
            "A Title",
            new PublicationDateDto(PUBLICATION_YEAR, null, null),
            List.of(contributor1, contributor2),
            defaultChannel(),
            defaultPages(),
            null);

    var document =
        new ReportDocument(
            UUID.randomUUID(),
            new ReportingPeriod(REPORTING_YEAR),
            GlobalApprovalStatus.APPROVED,
            new BigDecimal("1.0000"),
            INTERNATIONAL_COLLABORATION_FACTOR,
            2,
            publicationDetails,
            List.of(approval));

    var rows = InstitutionReportMapper.mapToReportRows(document, INSTITUTION_ID).toList();

    assertThat(rows).hasSize(2);
    assertThat(rows)
        .extracting(row -> cellValue(row, PERSONLOPENR))
        .containsExactlyInAnyOrder(CONTRIBUTOR_ID.toString(), secondContributorId.toString());
    assertThat(rows)
        .extracting(row -> cellValue(row, TENTATIVE_PUBLISERINGSPOENG))
        .containsExactlyInAnyOrder(points1.toPlainString(), points2.toPlainString());
  }

  @Test
  void shouldMapApprovalStatusCorrectly() {
    assertThat(cellValue(rowWithApprovalStatus(ApprovalStatus.APPROVED), STATUS_KONTROLLERT))
        .isEqualTo("J");
    assertThat(cellValue(rowWithApprovalStatus(ApprovalStatus.REJECTED), STATUS_KONTROLLERT))
        .isEqualTo("N");
    assertThat(cellValue(rowWithApprovalStatus(ApprovalStatus.PENDING), STATUS_KONTROLLERT))
        .isEqualTo("?");
    assertThat(cellValue(rowWithApprovalStatus(ApprovalStatus.NEW), STATUS_KONTROLLERT))
        .isEqualTo("?");
  }

  @Test
  void shouldMapGlobalApprovalStatusCorrectly() {
    assertThat(cellValue(rowWithGlobalStatus(GlobalApprovalStatus.APPROVED), RAPPORTSTATUS))
        .isEqualTo("J");
    assertThat(cellValue(rowWithGlobalStatus(GlobalApprovalStatus.REJECTED), RAPPORTSTATUS))
        .isEqualTo("N");
    assertThat(cellValue(rowWithGlobalStatus(GlobalApprovalStatus.PENDING), RAPPORTSTATUS))
        .isEqualTo("?");
    assertThat(cellValue(rowWithGlobalStatus(GlobalApprovalStatus.DISPUTE), RAPPORTSTATUS))
        .isEqualTo("T");
  }

  @Test
  void shouldReturnUnknownWhenInternationalCollaborationFactorIsNull() {
    var document =
        new ReportDocument(
            UUID.randomUUID(),
            new ReportingPeriod(REPORTING_YEAR),
            GlobalApprovalStatus.APPROVED,
            BigDecimal.ONE,
            null,
            1,
            defaultPublicationDetails(),
            List.of(defaultApproval(ApprovalStatus.APPROVED, BigDecimal.ONE)));

    var row =
        InstitutionReportMapper.mapToReportRows(document, INSTITUTION_ID).findFirst().orElseThrow();

    assertThat(cellValue(row, FAKTORTALL_SAMARBEID)).isEqualTo("N/A");
  }

  private static String cellValue(Row row, Header header) {
    return row.cells().stream()
        .filter(cell -> cell.header() == header)
        .findFirst()
        .map(Cell::string)
        .orElseThrow(() -> new AssertionError("No cell for header " + header));
  }

  private ReportDocument documentWithContributorAndApproval(BigDecimal points) {
    return new ReportDocument(
        UUID.randomUUID(),
        new ReportingPeriod(REPORTING_YEAR),
        GlobalApprovalStatus.APPROVED,
        new BigDecimal("1.0000"),
        INTERNATIONAL_COLLABORATION_FACTOR,
        1,
        defaultPublicationDetails(),
        List.of(defaultApproval(ApprovalStatus.APPROVED, points)));
  }

  private ReportDocument documentWithChannel(PublicationChannel channel) {
    var publicationDetails =
        new ReportPublicationDetails(
            PUBLICATION_ID_PREFIX + UUID.randomUUID(),
            PUBLICATION_TYPE,
            "A Title",
            new PublicationDateDto(PUBLICATION_YEAR, null, null),
            List.of(defaultContributor()),
            channel,
            defaultPages(),
            null);
    return documentWith(publicationDetails, ApprovalStatus.APPROVED);
  }

  private ReportDocument documentWith(
      ReportPublicationDetails publicationDetails, ApprovalStatus approvalStatus) {
    return new ReportDocument(
        UUID.randomUUID(),
        new ReportingPeriod(REPORTING_YEAR),
        GlobalApprovalStatus.APPROVED,
        new BigDecimal("1.0000"),
        INTERNATIONAL_COLLABORATION_FACTOR,
        1,
        publicationDetails,
        List.of(defaultApproval(approvalStatus, BigDecimal.ONE)));
  }

  private Row rowWithApprovalStatus(ApprovalStatus approvalStatus) {
    return InstitutionReportMapper.mapToReportRows(
            documentWith(defaultPublicationDetails(), approvalStatus), INSTITUTION_ID)
        .findFirst()
        .orElseThrow();
  }

  private Row rowWithGlobalStatus(GlobalApprovalStatus globalStatus) {
    var document =
        new ReportDocument(
            UUID.randomUUID(),
            new ReportingPeriod(REPORTING_YEAR),
            globalStatus,
            BigDecimal.ONE,
            INTERNATIONAL_COLLABORATION_FACTOR,
            1,
            defaultPublicationDetails(),
            List.of(defaultApproval(ApprovalStatus.APPROVED, BigDecimal.ONE)));
    return InstitutionReportMapper.mapToReportRows(document, INSTITUTION_ID)
        .findFirst()
        .orElseThrow();
  }

  private ReportPublicationDetails defaultPublicationDetails() {
    return new ReportPublicationDetails(
        PUBLICATION_ID_PREFIX + UUID.randomUUID(),
        PUBLICATION_TYPE,
        "Some Title",
        new PublicationDateDto(PUBLICATION_YEAR, null, null),
        List.of(defaultContributor()),
        defaultChannel(),
        defaultPages(),
        null);
  }

  private NviContributor defaultContributor() {
    var affiliation =
        NviOrganization.builder()
            .withId(AFFILIATION_ID)
            .withPartOf(List.of(INSTITUTION_ID))
            .build();
    return NviContributor.builder()
        .withId(CONTRIBUTOR_ID.toString())
        .withName("Doe, John")
        .withAffiliations(List.of(affiliation))
        .build();
  }

  private ReportApproval defaultApproval(ApprovalStatus approvalStatus, BigDecimal points) {
    var creatorAffiliationPointsView =
        CreatorAffiliationPointsView.builder()
            .withNviCreator(CONTRIBUTOR_ID)
            .withAffiliationId(AFFILIATION_ID)
            .withPoints(points)
            .build();
    var institutionPoints =
        InstitutionPointsView.builder()
            .withInstitutionId(INSTITUTION_ID)
            .withInstitutionPoints(points)
            .withCreatorAffiliationPoints(List.of(creatorAffiliationPointsView))
            .build();
    return new ReportApproval(INSTITUTION_ID, approvalStatus, institutionPoints);
  }

  private static PublicationChannel defaultChannel() {
    return PublicationChannel.builder()
        .withId(URI.create("https://example.org/channel/xyz"))
        .withType("Journal")
        .withScientificValue(ScientificValue.LEVEL_ONE)
        .withName("Some Journal")
        .withPrintIssn("1234-5678")
        .build();
  }

  private static Pages defaultPages() {
    return Pages.builder().withBegin("1").withEnd("10").withNumberOfPages("10").build();
  }
}
