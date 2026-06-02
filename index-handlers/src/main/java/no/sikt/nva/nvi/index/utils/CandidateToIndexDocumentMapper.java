package no.sikt.nva.nvi.index.utils;

import java.util.List;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.EnvironmentUriFactory;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import nva.commons.core.Environment;

/**
 * SPARQL/DTO-based index-document generator. Replaces the JsonNode-based {@link
 * NviCandidateIndexDocumentGenerator} once the cutover lands. Delegates to three focused
 * sub-mappers (contributors, approvals, publication details) and stitches the result together.
 */
public final class CandidateToIndexDocumentMapper implements IndexDocumentGenerator {

  private final Candidate candidate;
  private final Environment environment;
  private final ContributorMapper contributorMapper;
  private final ApprovalMapper approvalMapper;
  private final PublicationDetailsMapper publicationDetailsMapper;

  public CandidateToIndexDocumentMapper(
      Candidate candidate, PublicationDto publicationDto, Environment environment) {
    this.candidate = candidate;
    this.environment = environment;
    this.contributorMapper = new ContributorMapper(candidate, publicationDto);
    this.approvalMapper = new ApprovalMapper(candidate);
    this.publicationDetailsMapper = new PublicationDetailsMapper(candidate, publicationDto);
  }

  @Override
  public NviCandidateIndexDocument generate() {
    var contributors = contributorMapper.mapContributors();
    var approvals = approvalMapper.mapApprovals(contributors);
    var publicationDetails = publicationDetailsMapper.mapPublicationDetails(contributors);
    return buildDocument(approvals, publicationDetails);
  }

  private NviCandidateIndexDocument buildDocument(
      List<ApprovalView> approvals, PublicationDetails publicationDetails) {
    return NviCandidateIndexDocument.builder()
        .withId(EnvironmentUriFactory.candidateId(environment, candidate.identifier()))
        .withContext(EnvironmentUriFactory.context(environment))
        .withIsApplicable(candidate.isApplicable())
        .withIdentifier(candidate.identifier())
        .withReportingPeriod(ReportingPeriod.fromCandidate(candidate))
        .withReported(candidate.isReported())
        .withReportedDate(candidate.reportedDate())
        .withApprovals(approvals)
        .withPublicationDetails(publicationDetails)
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
