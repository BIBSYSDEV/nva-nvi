package no.sikt.nva.nvi.index.model.report;

import static java.util.Objects.nonNull;
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
import no.unit.nva.language.LanguageDescription;
import no.unit.nva.language.LanguageMapper;
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

  public static Stream<InstitutionReportRow> mapToRows(ReportDocument document, URI institutionId) {
    var approval = findApproval(document, institutionId);
    if (approval.isEmpty()) {
      LOGGER.warn(NO_APPROVAL_MESSAGE, institutionId, document.identifier());
      return Stream.empty();
    }
    return document.publicationDetails().nviContributors().stream()
        .flatMap(contributor -> mapToRows(document, approval.get(), contributor, institutionId));
  }

  private static Stream<InstitutionReportRow> mapToRows(
      ReportDocument document,
      ReportApproval approval,
      NviContributor contributor,
      URI institutionId) {
    return contributor
        .getAffiliationsPartOfOrEqualTo(institutionId)
        .map(affiliation -> toRow(document, approval, contributor, affiliation));
  }

  private static InstitutionReportRow toRow(
      ReportDocument document,
      ReportApproval approval,
      NviContributor contributor,
      NviOrganization affiliation) {
    var channel = document.publicationDetails().publicationChannel();
    var pointsForAffiliation =
        getPointsForAffiliation(approval, contributor, affiliation).toString();
    var globalStatus = mapGlobalStatus(document.globalApprovalStatus());
    return new InstitutionReportRow(
        document.reportingPeriod().year(),
        document.publicationDetails().id(),
        document.publicationDetails().type(),
        nonNull(channel.id()) ? channel.id().toString() : EMPTY_STRING,
        orEmpty(channel.type()),
        orEmpty(channel.printIssn()),
        orEmpty(channel.name()),
        channel.scientificValue().getValue(),
        contributor.id(),
        affiliation.identifier(),
        affiliation.id().toString(),
        affiliation.getInstitutionIdentifier(),
        affiliation.getFacultyIdentifier(),
        affiliation.getDepartmentIdentifier(),
        affiliation.getGroupIdentifier(),
        contributor.name(),
        contributor.name(),
        document.publicationDetails().title(),
        languageLabel(document.publicationDetails().language()),
        mapApprovalStatus(approval.approvalStatus()),
        globalStatus,
        document.publicationTypeChannelLevelPoints().toString(),
        getInternationalCollaborationFactor(document),
        String.valueOf(document.creatorShareCount()),
        pointsForAffiliation,
        getPublishingPoints(pointsForAffiliation, globalStatus));
  }

  private static String getPublishingPoints(String pointsForAffiliation, String globalStatus) {
    return APPROVED_VALUE.equals(globalStatus) ? pointsForAffiliation : BigDecimal.ZERO.toString();
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

  private static String languageLabel(String language) {
    return Optional.ofNullable(language)
        .map(URI::create)
        .map(LanguageMapper::getLanguageByUri)
        .map(LanguageDescription::getNob)
        .orElse(EMPTY_STRING);
  }

  private static String orEmpty(String value) {
    return Optional.ofNullable(value).orElse(EMPTY_STRING);
  }
}
