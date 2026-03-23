package no.sikt.nva.nvi.index.model.report;

import static java.util.Objects.nonNull;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import static nva.commons.core.attempt.Try.attempt;

import com.opencsv.bean.CsvToBeanBuilder;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView.CreatorAffiliationPointsView;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.report.model.Row;
import no.sikt.nva.nvi.report.model.institutionreport.ReportRowBuilder;
import nva.commons.core.ioutils.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InstitutionReportMapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(InstitutionReportMapper.class);
  private static final String NO_APPROVAL_MESSAGE =
      "No approval found for institution: {}. Cannot convert "
          + "candidate with id {} to report rows";
  private static final String J = "J";
  private static final String N = "N";
  private static final String PENDING_VALUE = "?";
  private static final String DISPUTED_VALUE = "T";
  private static final String UNKNOWN = "N/A";
  private static final String DBH_INSTITUTIONS = "dbh_institutions.csv";
  private static final Map<String, List<DbhCsvEntry>> INSTITUTIONS = loadInstitutions();
  private static final char CSV_DELIMITER = ';';

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

  public static BigDecimal getPointsForAffiliation(
      ReportApproval approval, NviContributor contributor, NviOrganization affiliation) {
    return approval.points().creatorAffiliationPoints().stream()
        .filter(pointsView -> pointsView.affiliationId().equals(affiliation.id()))
        .filter(pointsView -> pointsView.nviCreator().toString().equals(contributor.id()))
        .map(CreatorAffiliationPointsView::points)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private static Map<String, List<DbhCsvEntry>> loadInstitutions() {
    return attempt(() -> IoUtils.inputStreamFromResources(DBH_INSTITUTIONS))
        .map(InstitutionReportMapper::parseToCsv)
        .orElseThrow();
  }

  private static Map<String, List<DbhCsvEntry>> parseToCsv(InputStream inputStream) {
    return new CsvToBeanBuilder<DbhCsvEntry>(
            new InputStreamReader(inputStream, StandardCharsets.UTF_8))
            .withType(DbhCsvEntry.class)
            .withSeparator(CSV_DELIMITER)
            .withIgnoreLeadingWhiteSpace(true)
            .build()
            .parse()
            .stream()
            .collect(Collectors.groupingBy(DbhCsvEntry::nvaTopLevelIdentifier));
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
    var institutionIdentifier = affiliation.getInstitutionIdentifier();
    var dbhCsvEntry = getDbhCsvEntry(affiliation.identifier(), institutionIdentifier);
    return new ReportRowBuilder()
        .withYear(document.year())
        .withPublicationId(document.publicationId())
        .withPublicationType(document.publicationType())
        .withPublicationChannel(nonNull(channel.id()) ? channel.id().toString() : EMPTY_STRING)
        .withPublicationChannelType(orEmpty(channel.type()))
        .withPrintIssn(orEmpty(channel.printIssn()))
        .withPublicationChannelName(orEmpty(channel.name()))
        .withScientificValue(channel.scientificValue().getValue())
        .withContributorId(contributor.id())
        .withAffiliationIdentifier(affiliation.identifier())
        .withAffiliationId(affiliation.id().toString())
        .withDbhInstitutionCode(dbhCsvEntry.map(DbhCsvEntry::institution).orElse(EMPTY_STRING))
        .withDbhFacultyCode(dbhCsvEntry.map(DbhCsvEntry::faculty).orElse(EMPTY_STRING))
        .withDbhDepartmentCode(dbhCsvEntry.map(DbhCsvEntry::department).orElse(EMPTY_STRING))
        .withSector(approval.sector())
        .withRboStatus(getRboStatus(approval.rboInstitution()))
        .withInstitutionNumber(institutionIdentifier)
        .withFacultyNumber(affiliation.getFacultyIdentifier())
        .withDepartmentNumber(affiliation.getDepartmentIdentifier())
        .withGroupNumber(affiliation.getGroupIdentifier())
        .withLastName(contributor.name())
        .withFirstName(contributor.name())
        .withTitle(document.publicationDetails().title())
        .withApprovalStatus(mapApprovalStatus(approval.approvalStatus()))
        .withGlobalStatus(globalStatus)
        .withInternationalCollaborationFactor(getInternationalCollaborationFactor(document))
        .withPublicationTypeChannelLevelPoints(document.publicationTypeChannelLevelPoints())
        .withCreatorShareCount(BigDecimal.valueOf(document.creatorShareCount()))
        .withTentativePublishingPoints(pointsForAffiliation)
        .withPublishingPoints(getPublishingPoints(pointsForAffiliation, globalStatus))
        .build();
  }

  private static String getRboStatus(boolean rboInstitution) {
    return rboInstitution ? J : N;
  }

  private static Optional<DbhCsvEntry> getDbhCsvEntry(
      String institutionIdentifier, String institutionNumber) {
    return Optional.ofNullable(INSTITUTIONS.get(institutionNumber))
        .flatMap(list -> getInstitutionWithSpecificIdentifier(institutionIdentifier, list))
        .or(() -> getTopLevelDbhEntry(institutionNumber))
        .or(() -> getAnyDbhEntryWithInstitutionNumber(institutionNumber));
  }

  private static Optional<DbhCsvEntry> getTopLevelDbhEntry(String institutionNumber) {
    return Optional.ofNullable(INSTITUTIONS.get(institutionNumber))
        .map(list -> list.stream().filter(DbhCsvEntry::isTopLevel).toList())
        .map(List::getFirst);
  }

  private static Optional<DbhCsvEntry> getAnyDbhEntryWithInstitutionNumber(
      String institutionNumber) {
    return Optional.ofNullable(INSTITUTIONS.get(institutionNumber)).map(List::getFirst);
  }

  private static Optional<DbhCsvEntry> getInstitutionWithSpecificIdentifier(
      String institutionIdentifier, List<DbhCsvEntry> list) {
    return list.stream()
        .filter(entry -> entry.fullNvaIdentifier().equals(institutionIdentifier))
        .findFirst();
  }

  private static BigDecimal getPublishingPoints(
      BigDecimal pointsForAffiliation, String globalStatus) {
    return J.equals(globalStatus) ? pointsForAffiliation : BigDecimal.ZERO;
  }

  private static String getInternationalCollaborationFactor(ReportDocument document) {
    return Optional.ofNullable(document.internationalCollaborationFactor())
        .map(String::valueOf)
        .orElse(UNKNOWN);
  }

  private static Optional<ReportApproval> findApproval(ReportDocument document, URI institutionId) {
    return document.approvals().stream()
        .filter(approval -> approval.institutionId().equals(institutionId))
        .findAny();
  }

  private static String mapApprovalStatus(ApprovalStatus status) {
    return switch (status) {
      case APPROVED -> J;
      case REJECTED -> N;
      case NEW, PENDING -> PENDING_VALUE;
    };
  }

  private static String mapGlobalStatus(GlobalApprovalStatus status) {
    return switch (status) {
      case APPROVED -> J;
      case REJECTED -> N;
      case PENDING -> PENDING_VALUE;
      case DISPUTE -> DISPUTED_VALUE;
    };
  }

  private static String orEmpty(String value) {
    return Optional.ofNullable(value).orElse(EMPTY_STRING);
  }
}
