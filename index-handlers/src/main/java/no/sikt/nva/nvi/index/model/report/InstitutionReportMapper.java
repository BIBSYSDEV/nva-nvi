package no.sikt.nva.nvi.index.model.report;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.report.model.Header.ARSTALL;
import static no.sikt.nva.nvi.index.report.model.Header.AVDNR;
import static no.sikt.nva.nvi.index.report.model.Header.ETTERNAVN;
import static no.sikt.nva.nvi.index.report.model.Header.FAKTORTALL_SAMARBEID;
import static no.sikt.nva.nvi.index.report.model.Header.FORFATTERDEL;
import static no.sikt.nva.nvi.index.report.model.Header.FORNAVN;
import static no.sikt.nva.nvi.index.report.model.Header.GRUPPENR;
import static no.sikt.nva.nvi.index.report.model.Header.INSTITUSJON;
import static no.sikt.nva.nvi.index.report.model.Header.INSTITUSJONSNR;
import static no.sikt.nva.nvi.index.report.model.Header.INSTITUSJON_ID;
import static no.sikt.nva.nvi.index.report.model.Header.KVALITETSNIVAKODE;
import static no.sikt.nva.nvi.index.report.model.Header.NVAID;
import static no.sikt.nva.nvi.index.report.model.Header.PERSONLOPENR;
import static no.sikt.nva.nvi.index.report.model.Header.PRINT_ISSN;
import static no.sikt.nva.nvi.index.report.model.Header.PUBLIKASJONSFORM;
import static no.sikt.nva.nvi.index.report.model.Header.PUBLISERINGSKANAL;
import static no.sikt.nva.nvi.index.report.model.Header.PUBLISERINGSKANALNAVN;
import static no.sikt.nva.nvi.index.report.model.Header.PUBLISERINGSKANALTYPE;
import static no.sikt.nva.nvi.index.report.model.Header.PUBLISERINGSPOENG;
import static no.sikt.nva.nvi.index.report.model.Header.RAPPORTSTATUS;
import static no.sikt.nva.nvi.index.report.model.Header.STATUS_KONTROLLERT;
import static no.sikt.nva.nvi.index.report.model.Header.TENTATIVE_PUBLISERINGSPOENG;
import static no.sikt.nva.nvi.index.report.model.Header.TITTEL;
import static no.sikt.nva.nvi.index.report.model.Header.UNDAVDNR;
import static no.sikt.nva.nvi.index.report.model.Header.VEKTINGSTALL;
import static nva.commons.core.StringUtils.EMPTY_STRING;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView.CreatorAffiliationPointsView;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.report.model.Cell;
import no.sikt.nva.nvi.index.report.model.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InstitutionReportMapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(InstitutionReportMapper.class);
  private static final String NO_APPROVAL_MESSAGE =
      "No approval found for institution: {}. Cannot convert candidate with id {} to report rows";
  private static final String APPROVED_VALUE = "J";
  private static final String REJECTED_VALUE = "N";
  private static final String PENDING_VALUE = "?";
  private static final String DISPUTED_VALUE = "T";
  private static final String UNKNOWN = "N/A";

  private InstitutionReportMapper() {}

  public static Stream<Row> mapToReportRows(ReportDocument document, URI institutionId) {
    var approval = findApproval(document, institutionId);
    if (approval.isEmpty()) {
      LOGGER.warn(NO_APPROVAL_MESSAGE, institutionId, document.identifier());
      return Stream.empty();
    }
    return document.publicationDetails().nviContributors().stream()
        .flatMap(
            contributor -> mapToReportRows(document, approval.get(), contributor, institutionId));
  }

  private static Stream<Row> mapToReportRows(
      ReportDocument document,
      ReportApproval approval,
      NviContributor contributor,
      URI institutionId) {
    return contributor
        .getAffiliationsPartOfOrEqualTo(institutionId)
        .map(affiliation -> toReportRow(document, approval, contributor, affiliation));
  }

  private static Row toReportRow(
      ReportDocument document,
      ReportApproval approval,
      NviContributor contributor,
      NviOrganization affiliation) {
    var channel = document.publicationDetails().publicationChannel();
    var pointsForAffiliation = getPointsForAffiliation(approval, contributor, affiliation);
    var globalStatus = mapGlobalStatus(document.globalApprovalStatus());
    return Row.builder()
        .withCell(Cell.of(ARSTALL, document.reportingPeriod().year()))
        .withCell(Cell.of(NVAID, document.publicationDetails().id()))
        .withCell(Cell.of(PUBLIKASJONSFORM, document.publicationDetails().type()))
        .withCell(
            Cell.of(
                PUBLISERINGSKANAL, nonNull(channel.id()) ? channel.id().toString() : EMPTY_STRING))
        .withCell(Cell.of(PUBLISERINGSKANALTYPE, orEmpty(channel.type())))
        .withCell(Cell.of(PRINT_ISSN, orEmpty(channel.printIssn())))
        .withCell(Cell.of(PUBLISERINGSKANALNAVN, orEmpty(channel.name())))
        .withCell(Cell.of(KVALITETSNIVAKODE, channel.scientificValue().getValue()))
        .withCell(Cell.of(PERSONLOPENR, contributor.id()))
        .withCell(Cell.of(INSTITUSJON, affiliation.identifier()))
        .withCell(Cell.of(INSTITUSJON_ID, affiliation.id().toString()))
        .withCell(Cell.of(INSTITUSJONSNR, affiliation.getInstitutionIdentifier()))
        .withCell(Cell.of(AVDNR, affiliation.getFacultyIdentifier()))
        .withCell(Cell.of(UNDAVDNR, affiliation.getDepartmentIdentifier()))
        .withCell(Cell.of(GRUPPENR, affiliation.getGroupIdentifier()))
        .withCell(Cell.of(ETTERNAVN, contributor.name()))
        .withCell(Cell.of(FORNAVN, contributor.name()))
        .withCell(Cell.of(TITTEL, document.publicationDetails().title()))
        .withCell(Cell.of(STATUS_KONTROLLERT, mapApprovalStatus(approval.approvalStatus())))
        .withCell(Cell.of(RAPPORTSTATUS, globalStatus))
        .withCell(Cell.of(FAKTORTALL_SAMARBEID, getInternationalCollaborationFactor(document)))
        .withCell(Cell.of(VEKTINGSTALL, document.publicationTypeChannelLevelPoints()))
        .withCell(Cell.of(FORFATTERDEL, BigDecimal.valueOf(document.creatorShareCount())))
        .withCell(Cell.of(TENTATIVE_PUBLISERINGSPOENG, pointsForAffiliation))
        .withCell(
            Cell.of(PUBLISERINGSPOENG, getPublishingPoints(pointsForAffiliation, globalStatus)))
        .build();
  }

  private static BigDecimal getPublishingPoints(
      BigDecimal pointsForAffiliation, String globalStatus) {
    return APPROVED_VALUE.equals(globalStatus) ? pointsForAffiliation : BigDecimal.ZERO;
  }

  private static String getInternationalCollaborationFactor(ReportDocument document) {
    return Optional.ofNullable(document.internationalCollaborationFactor())
        .map(String::valueOf)
        .orElse(UNKNOWN);
  }

  public static BigDecimal getPointsForAffiliation(
      ReportApproval approval, NviContributor contributor, NviOrganization affiliation) {
    return approval.points().creatorAffiliationPoints().stream()
        .filter(pointsView -> pointsView.affiliationId().equals(affiliation.id()))
        .filter(pointsView -> pointsView.nviCreator().toString().equals(contributor.id()))
        .map(CreatorAffiliationPointsView::points)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private static Optional<ReportApproval> findApproval(ReportDocument document, URI institutionId) {
    return document.approvals().stream()
        .filter(approval -> approval.institutionId().equals(institutionId))
        .findAny();
  }

  private static String mapApprovalStatus(ApprovalStatus status) {
    return switch (status) {
      case APPROVED -> APPROVED_VALUE;
      case REJECTED -> REJECTED_VALUE;
      case NEW, PENDING -> PENDING_VALUE;
    };
  }

  private static String mapGlobalStatus(GlobalApprovalStatus status) {
    return switch (status) {
      case APPROVED -> APPROVED_VALUE;
      case REJECTED -> REJECTED_VALUE;
      case PENDING -> PENDING_VALUE;
      case DISPUTE -> DISPUTED_VALUE;
    };
  }

  private static String orEmpty(String value) {
    return Optional.ofNullable(value).orElse(EMPTY_STRING);
  }
}
