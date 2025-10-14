package no.sikt.nva.nvi.common.service;

import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.model.NviCreator.isAffiliatedWithTopLevelOrganization;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.permissions.CandidatePermissions;
import no.sikt.nva.nvi.common.service.dto.ApprovalDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.dto.NoteDto;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.dto.problem.CandidateProblem;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorFromOrganizationProblem;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorProblem;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.Note;

public final class CandidateResponseFactory {

  private CandidateResponseFactory() {}

  public static CandidateDto create(Candidate candidate, UserInstance userInstance) {
    return CandidateDto.builder()
        .withId(candidate.getId())
        .withContext(Candidate.getContextUri())
        .withIdentifier(candidate.getIdentifier())
        .withPublicationId(candidate.getPublicationId())
        .withApprovals(getApprovalsAsDto(candidate))
        .withAllowedOperations(getAllowedOperations(candidate, userInstance))
        .withProblems(getProblems(candidate, userInstance))
        .withNotes(getNotesAsDto(candidate))
        .withPeriod(getPeriodStatusDto(candidate))
        .withTotalPoints(candidate.getTotalPoints())
        .withReportStatus(getReportStatus(candidate))
        .build();
  }

  private static PeriodStatusDto getPeriodStatusDto(Candidate candidate) {
    return PeriodStatusDto.fromPeriodStatus(candidate.getPeriod());
  }

  private static List<NoteDto> getNotesAsDto(Candidate candidate) {
    return candidate.getNotes().values().stream().map(Note::toDto).toList();
  }

  private static List<ApprovalDto> getApprovalsAsDto(Candidate candidate) {
    return candidate.getApprovals().values().stream().map(mapToApprovalDto(candidate)).toList();
  }

  private static Function<Approval, ApprovalDto> mapToApprovalDto(Candidate candidate) {
    return approval ->
        ApprovalDto.fromApprovalAndInstitutionPoints(
            approval, candidate.getPointValueForInstitution(approval.institutionId()));
  }

  private static String getReportStatus(Candidate candidate) {
    return Optional.ofNullable(candidate.getReportStatus())
        .map(ReportStatus::getValue)
        .orElse(null);
  }

  private static Set<CandidateOperation> getAllowedOperations(
      Candidate candidate, UserInstance userInstance) {
    var permissions = CandidatePermissions.create(candidate, userInstance);
    return permissions.getAllAllowedActions();
  }

  private static Set<CandidateProblem> getProblems(Candidate candidate, UserInstance userInstance) {
    var problems = new HashSet<CandidateProblem>();

    if (hasUnverifiedCreators(candidate)) {
      problems.add(new UnverifiedCreatorProblem());
    }

    var unverifiedCreatorsFromUserOrganization =
        getUnverifiedCreatorNames(candidate, userInstance.topLevelOrganizationId());
    if (!unverifiedCreatorsFromUserOrganization.isEmpty()) {
      problems.add(
          new UnverifiedCreatorFromOrganizationProblem(unverifiedCreatorsFromUserOrganization));
    }

    return problems;
  }

  private static boolean hasUnverifiedCreators(Candidate candidate) {
    return candidate.getPublicationDetails().nviCreators().stream()
        .anyMatch(not(NviCreator::isVerified));
  }

  private static List<String> getUnverifiedCreatorNames(Candidate candidate, URI organizationId) {
    return candidate.getPublicationDetails().nviCreators().stream()
        .filter(not(NviCreator::isVerified))
        .filter(isAffiliatedWithTopLevelOrganization(organizationId))
        .map(NviCreator::name)
        .toList();
  }
}
