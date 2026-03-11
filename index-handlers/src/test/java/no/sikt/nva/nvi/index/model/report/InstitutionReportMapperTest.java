package no.sikt.nva.nvi.index.model.report;

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

    var rows = InstitutionReportMapper.mapReportDocumentToRows(document, INSTITUTION_ID).toList();

    assertThat(rows).hasSize(1);
    var row = rows.getFirst();
    assertThat(row.reportingYear()).isEqualTo(REPORTING_YEAR);
    assertThat(row.publishedYear()).isEqualTo(PUBLICATION_YEAR);
    assertThat(row.institutionApprovalStatus()).isEqualTo("J");
    assertThat(row.globalStatus()).isEqualTo("J");
    assertThat(row.contributorIdentifier()).isEqualTo(CONTRIBUTOR_ID.toString());
    assertThat(row.pointsForAffiliation()).isEqualTo(points.toString());
  }

  @Test
  void shouldPopulateOrganizationIdentifiersFromAffiliation() {
    var document = documentWithContributorAndApproval(BigDecimal.ONE);

    var row =
        InstitutionReportMapper.mapReportDocumentToRows(document, INSTITUTION_ID)
            .findFirst()
            .orElseThrow();

    assertThat(row.institutionId()).isEqualTo("185");
    assertThat(row.facultyId()).isEqualTo("15");
    assertThat(row.departmentId()).isEqualTo("10");
    assertThat(row.groupId()).isEqualTo("5");
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
        InstitutionReportMapper.mapReportDocumentToRows(document, INSTITUTION_ID)
            .findFirst()
            .orElseThrow();

    assertThat(row.publicationChannel()).isEqualTo(channelId.toString());
    assertThat(row.publicationChannelType()).isEqualTo("Journal");
    assertThat(row.publicationChannelPissn()).isEqualTo("1234-5678");
    assertThat(row.publicationChannelLevel()).isEqualTo(ScientificValue.LEVEL_ONE.getValue());
    assertThat(row.publicationChannelName()).isEqualTo("Some Journal");
  }

  @Test
  void shouldReturnEmptyStreamWhenNoApprovalExistsForInstitution() {
    var otherInstitutionId = URI.create("https://example.org/organization/999");
    var document = documentWithContributorAndApproval(BigDecimal.ONE);

    var rows =
        InstitutionReportMapper.mapReportDocumentToRows(document, otherInstitutionId).toList();

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

    var cap1 =
        CreatorAffiliationPointsView.builder()
            .withNviCreator(CONTRIBUTOR_ID)
            .withAffiliationId(AFFILIATION_ID)
            .withPoints(points1)
            .build();
    var cap2 =
        CreatorAffiliationPointsView.builder()
            .withNviCreator(secondContributorId)
            .withAffiliationId(secondAffiliationId)
            .withPoints(points2)
            .build();
    var institutionPoints =
        InstitutionPointsView.builder()
            .withInstitutionId(INSTITUTION_ID)
            .withInstitutionPoints(new BigDecimal("0.7000"))
            .withCreatorAffiliationPoints(List.of(cap1, cap2))
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
        new NviCandidateReportDocument(
            UUID.randomUUID(),
            new ReportingPeriod(REPORTING_YEAR),
            GlobalApprovalStatus.APPROVED,
            new BigDecimal("1.0000"),
            INTERNATIONAL_COLLABORATION_FACTOR,
            2,
            publicationDetails,
            List.of(approval));

    var rows = InstitutionReportMapper.mapReportDocumentToRows(document, INSTITUTION_ID).toList();

    assertThat(rows).hasSize(2);
    assertThat(rows)
        .extracting(InstitutionReportRow::contributorIdentifier)
        .containsExactlyInAnyOrder(CONTRIBUTOR_ID.toString(), secondContributorId.toString());
    assertThat(rows)
        .extracting(InstitutionReportRow::pointsForAffiliation)
        .containsExactlyInAnyOrder(points1.toString(), points2.toString());
  }

  @Test
  void shouldMapApprovalStatusCorrectly() {
    assertThat(rowWithApprovalStatus(ApprovalStatus.APPROVED).institutionApprovalStatus())
        .isEqualTo("J");
    assertThat(rowWithApprovalStatus(ApprovalStatus.REJECTED).institutionApprovalStatus())
        .isEqualTo("N");
    assertThat(rowWithApprovalStatus(ApprovalStatus.PENDING).institutionApprovalStatus())
        .isEqualTo("?");
    assertThat(rowWithApprovalStatus(ApprovalStatus.NEW).institutionApprovalStatus())
        .isEqualTo("?");
  }

  @Test
  void shouldMapGlobalApprovalStatusCorrectly() {
    assertThat(rowWithGlobalStatus(GlobalApprovalStatus.APPROVED).globalStatus()).isEqualTo("J");
    assertThat(rowWithGlobalStatus(GlobalApprovalStatus.REJECTED).globalStatus()).isEqualTo("N");
    assertThat(rowWithGlobalStatus(GlobalApprovalStatus.PENDING).globalStatus()).isEqualTo("?");
    assertThat(rowWithGlobalStatus(GlobalApprovalStatus.DISPUTE).globalStatus()).isEqualTo("T");
  }

  @Test
  void shouldReturnEmptyPagesWhenPagesIsNull() {
    var publicationDetails =
        new ReportPublicationDetails(
            PUBLICATION_ID_PREFIX + UUID.randomUUID(),
            PUBLICATION_TYPE,
            "A Title",
            new PublicationDateDto(PUBLICATION_YEAR, null, null),
            List.of(defaultContributor()),
            defaultChannel(),
            null,
            null);
    var document = documentWith(publicationDetails, ApprovalStatus.APPROVED, BigDecimal.ONE);

    var row =
        InstitutionReportMapper.mapReportDocumentToRows(document, INSTITUTION_ID)
            .findFirst()
            .orElseThrow();

    assertThat(row.pageBegin()).isEmpty();
    assertThat(row.pageEnd()).isEmpty();
    assertThat(row.pageCount()).isEmpty();
  }

  @Test
  void shouldReturnUnknownWhenInternationalCollaborationFactorIsNull() {
    var document =
        new NviCandidateReportDocument(
            UUID.randomUUID(),
            new ReportingPeriod(REPORTING_YEAR),
            GlobalApprovalStatus.APPROVED,
            BigDecimal.ONE,
            null,
            1,
            defaultPublicationDetails(),
            List.of(defaultApproval(ApprovalStatus.APPROVED, BigDecimal.ONE)));

    var row =
        InstitutionReportMapper.mapReportDocumentToRows(document, INSTITUTION_ID)
            .findFirst()
            .orElseThrow();

    assertThat(row.internationalCollaborationFactor()).isEqualTo("N/A");
  }

  // --- helpers ---

  private NviCandidateReportDocument documentWithContributorAndApproval(BigDecimal points) {
    return new NviCandidateReportDocument(
        UUID.randomUUID(),
        new ReportingPeriod(REPORTING_YEAR),
        GlobalApprovalStatus.APPROVED,
        new BigDecimal("1.0000"),
        INTERNATIONAL_COLLABORATION_FACTOR,
        1,
        defaultPublicationDetails(),
        List.of(defaultApproval(ApprovalStatus.APPROVED, points)));
  }

  private NviCandidateReportDocument documentWithChannel(PublicationChannel channel) {
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
    return documentWith(publicationDetails, ApprovalStatus.APPROVED, BigDecimal.ONE);
  }

  private NviCandidateReportDocument documentWith(
      ReportPublicationDetails publicationDetails,
      ApprovalStatus approvalStatus,
      BigDecimal points) {
    return new NviCandidateReportDocument(
        UUID.randomUUID(),
        new ReportingPeriod(REPORTING_YEAR),
        GlobalApprovalStatus.APPROVED,
        new BigDecimal("1.0000"),
        INTERNATIONAL_COLLABORATION_FACTOR,
        1,
        publicationDetails,
        List.of(defaultApproval(approvalStatus, points)));
  }

  private InstitutionReportRow rowWithApprovalStatus(ApprovalStatus approvalStatus) {
    return InstitutionReportMapper.mapReportDocumentToRows(
            documentWith(defaultPublicationDetails(), approvalStatus, BigDecimal.ONE),
            INSTITUTION_ID)
        .findFirst()
        .orElseThrow();
  }

  private InstitutionReportRow rowWithGlobalStatus(GlobalApprovalStatus globalStatus) {
    var document =
        new NviCandidateReportDocument(
            UUID.randomUUID(),
            new ReportingPeriod(REPORTING_YEAR),
            globalStatus,
            BigDecimal.ONE,
            INTERNATIONAL_COLLABORATION_FACTOR,
            1,
            defaultPublicationDetails(),
            List.of(defaultApproval(ApprovalStatus.APPROVED, BigDecimal.ONE)));
    return InstitutionReportMapper.mapReportDocumentToRows(document, INSTITUTION_ID)
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
    var cap =
        CreatorAffiliationPointsView.builder()
            .withNviCreator(CONTRIBUTOR_ID)
            .withAffiliationId(AFFILIATION_ID)
            .withPoints(points)
            .build();
    var institutionPoints =
        InstitutionPointsView.builder()
            .withInstitutionId(INSTITUTION_ID)
            .withInstitutionPoints(points)
            .withCreatorAffiliationPoints(List.of(cap))
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
