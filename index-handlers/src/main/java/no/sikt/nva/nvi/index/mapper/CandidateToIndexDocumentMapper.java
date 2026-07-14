package no.sikt.nva.nvi.index.mapper;

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
 * Builds the index document from the persisted {@link Candidate}, which is the source of truth for
 * all NVI data, enriched with a {@link PublicationDto} where the Candidate has no data (non-NVI
 * contributors, ORCID, role, channel name/ISSN). The {@code PublicationDto} may be null, in which
 * case a lean Candidate-only document is produced.
 *
 * <p>The sub-mappers ({@code ContributorMapper}, {@code ApprovalMapper}, {@code
 * PublicationDetailsMapper}) share a method-naming vocabulary:
 *
 * <ul>
 *   <li>{@code map*} - transform an input into an index-document type (per element or collection)
 *   <li>{@code build*} - assemble an output object via a builder
 *   <li>{@code find*} - a lookup that may miss; returns an {@link java.util.Optional}
 *   <li>{@code extract*} - derive a single field/value from the source(s)
 *   <li>{@code <field>From<Source>} - the named {@code Optional} sources composed in an "X or Y or
 *       default" fallback chain (e.g. {@code labelsFromCandidate})
 * </ul>
 */
public final class CandidateToIndexDocumentMapper {

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
    this.approvalMapper = new ApprovalMapper(candidate, publicationDto);
    this.publicationDetailsMapper = new PublicationDetailsMapper(candidate, publicationDto);
  }

  public NviCandidateIndexDocument generate() {
    var contributors = contributorMapper.mapPublicationContributors();
    var nviContributors = contributorMapper.mapNviContributors();
    var approvals = approvalMapper.mapApprovals(nviContributors);
    var publicationDetails =
        publicationDetailsMapper.mapPublicationDetails(contributors, nviContributors);
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
