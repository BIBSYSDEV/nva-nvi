package no.sikt.nva.nvi.index.report.model;

import java.math.BigDecimal;
import java.util.Map;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;

/**
 * Groups {@link CandidateTotal} values by {@link ApprovalStatus} for a single institution.
 *
 * @param totalsByStatus candidate totals keyed by the institution's local approval status
 */
public record LocalStatusSummary(Map<ApprovalStatus, CandidateTotal> totalsByStatus) {

  public CandidateTotal total() {
    return totalsByStatus.values().stream()
        .reduce(CandidateTotal.EMPTY_CANDIDATE_TOTAL, CandidateTotal::add);
  }

  public CandidateTotal forStatus(ApprovalStatus status) {
    return totalsByStatus.getOrDefault(status, CandidateTotal.EMPTY_CANDIDATE_TOTAL);
  }

  public int totalCount() {
    return total().candidateCount();
  }

  public BigDecimal totalPoints() {
    return total().totalPoints();
  }
}
