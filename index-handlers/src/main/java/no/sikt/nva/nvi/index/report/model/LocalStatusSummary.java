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

  /** Returns the combined {@link CandidateTotal} across all statuses. */
  public CandidateTotal total() {
    return totalsByStatus.values().stream()
        .reduce(CandidateTotal.EMPTY_CANDIDATE_TOTAL, CandidateTotal::add);
  }

  /**
   * Returns the {@link CandidateTotal} for the given status, or {@link
   * CandidateTotal#EMPTY_CANDIDATE_TOTAL} if absent.
   */
  public CandidateTotal forStatus(ApprovalStatus status) {
    return totalsByStatus.getOrDefault(status, CandidateTotal.EMPTY_CANDIDATE_TOTAL);
  }

  /** Returns the total candidate count across all statuses. */
  public int totalCount() {
    return total().candidateCount();
  }

  /** Returns the total NVI points across all statuses. */
  public BigDecimal totalPoints() {
    return total().totalPoints();
  }
}
