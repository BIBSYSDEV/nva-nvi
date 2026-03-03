package no.sikt.nva.nvi.index.report.model;

import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;

/**
 * Groups {@link CandidateTotal} values by {@link GlobalApprovalStatus} for an entire reporting
 * period.
 *
 * @param totalsByStatus candidate totals keyed by the candidate's global approval status
 */
public record GlobalStatusSummary(Map<GlobalApprovalStatus, CandidateTotal> totalsByStatus) {

  public CandidateTotal forStatus(GlobalApprovalStatus status) {
    return totalsByStatus.getOrDefault(status, CandidateTotal.EMPTY_CANDIDATE_TOTAL);
  }

  public CandidateTotal undisputed() {
    return totalsByStatus.entrySet().stream()
        .filter(entry -> entry.getKey() != GlobalApprovalStatus.DISPUTE)
        .map(Map.Entry::getValue)
        .reduce(CandidateTotal.EMPTY_CANDIDATE_TOTAL, CandidateTotal::add);
  }
}
