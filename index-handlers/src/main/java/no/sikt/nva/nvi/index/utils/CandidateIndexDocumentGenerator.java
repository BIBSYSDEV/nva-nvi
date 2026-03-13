package no.sikt.nva.nvi.index.utils;

import java.util.List;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;

public final class CandidateIndexDocumentGenerator {

  private final Candidate candidate;
  private final ContributorExpander contributorExpander;
  private final ApprovalExpander approvalExpander;
  private final PublicationDetailsExpander publicationDetailsExpander;

  public CandidateIndexDocumentGenerator(Candidate candidate, PublicationDto publicationDto) {
    this.candidate = candidate;
    this.contributorExpander = new ContributorExpander(candidate, publicationDto);
    this.approvalExpander = new ApprovalExpander(candidate);
    this.publicationDetailsExpander = new PublicationDetailsExpander(candidate, publicationDto);
  }

  public NviCandidateIndexDocument generateDocument() {
    var expandedContributors = contributorExpander.expandContributors();
    var approvals = approvalExpander.createApprovals(expandedContributors);
    var expandedPublicationDetails =
        publicationDetailsExpander.expandPublicationDetails(expandedContributors);
    return buildDocument(approvals, expandedPublicationDetails);
  }

  private NviCandidateIndexDocument buildDocument(
      List<ApprovalView> approvals, PublicationDetails expandedPublicationDetails) {
    return NviCandidateIndexDocument.builder()
        .withId(candidate.getId())
        .withContext(candidate.getContextUri())
        .withIsApplicable(candidate.isApplicable())
        .withIdentifier(candidate.identifier())
        .withReportingPeriod(ReportingPeriod.fromCandidate(candidate))
        .withReported(candidate.isReported())
        .withApprovals(approvals)
        .withPublicationDetails(expandedPublicationDetails)
        .withNumberOfApprovals(approvals.size())
        .withPoints(candidate.getTotalPoints())
        .withPublicationTypeChannelLevelPoints(candidate.getBasePoints())
        .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
        .withCreatorShareCount(candidate.getCreatorShareCount())
        .withInternationalCollaborationFactor(candidate.getCollaborationFactor())
        .withCreatedDate(candidate.createdDate())
        .withModifiedDate(candidate.modifiedDate())
        .build();
  }
}
