package no.sikt.nva.nvi.index.model.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import no.sikt.nva.nvi.index.report.ReportRow;

public record InstitutionReportRow(
    String reportingYear,
    String publicationIdentifier,
    String publishedYear,
    String institutionApprovalStatus,
    String publicationInstance,
    String publicationChannel,
    String publicationChannelType,
    String publicationChannelPissn,
    String publicationChannelLevel,
    String contributorIdentifier,
    String institutionId,
    String facultyId,
    String departmentId,
    String groupId,
    String contributorLastName,
    String contributorFirstName,
    String publicationChannelName,
    String pageBegin,
    String pageEnd,
    String pageCount,
    String publicationTitle,
    String publicationLanguage,
    String globalStatus,
    String publicationChannelLevelPoints,
    String internationalCollaborationFactor,
    String creatorShareCount,
    String pointsForAffiliation)
    implements ReportRow {

  @Override
  public List<String> toRow() {
    return new ArrayList<>(
        Arrays.asList(
            reportingYear,
            publicationIdentifier,
            publishedYear,
            institutionApprovalStatus,
            publicationInstance,
            publicationChannel,
            publicationChannelType,
            publicationChannelPissn,
            publicationChannelLevel,
            contributorIdentifier,
            institutionId,
            facultyId,
            departmentId,
            groupId,
            contributorLastName,
            contributorFirstName,
            publicationChannelName,
            pageBegin,
            pageEnd,
            pageCount,
            publicationTitle,
            publicationLanguage,
            globalStatus,
            publicationChannelLevelPoints,
            internationalCollaborationFactor,
            creatorShareCount,
            pointsForAffiliation));
  }
}
