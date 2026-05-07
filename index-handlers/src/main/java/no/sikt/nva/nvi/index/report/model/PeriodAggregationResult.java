package no.sikt.nva.nvi.index.report.model;

import static java.util.function.Predicate.not;

import java.math.BigDecimal;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;

/**
 * Aggregated NVI candidate data for an entire reporting period. Contains candidate totals grouped
 * by global approval status.
 *
 * @param period the NVI reporting period these results belong to
 * @param byGlobalStatus candidate totals grouped by the candidate's global approval status
 */
public record PeriodAggregationResult(
    NviPeriod period, Map<GlobalApprovalStatus, CandidateTotal> byGlobalStatus) {

  public PeriodAggregationResult {
    byGlobalStatus = Map.copyOf(byGlobalStatus);
  }

  /**
   * Returns the {@link CandidateTotal} for the given status, or {@link
   * CandidateTotal#EMPTY_CANDIDATE_TOTAL} if absent.
   */
  public CandidateTotal forStatus(GlobalApprovalStatus status) {
    return byGlobalStatus.getOrDefault(status, CandidateTotal.EMPTY_CANDIDATE_TOTAL);
  }

  /** Returns the total number of disputed candidates. */
  public int disputedCount() {
    return forStatus(GlobalApprovalStatus.DISPUTE).candidateCount();
  }

  /** Returns the total number of undisputed candidates. */
  public int undisputedTotalCount() {
    return undisputed().candidateCount();
  }

  /** Returns the number of candidates that have been globally approved or rejected. */
  public int undisputedProcessedCount() {
    var approved = forStatus(GlobalApprovalStatus.APPROVED);
    var rejected = forStatus(GlobalApprovalStatus.REJECTED);
    return approved.candidateCount() + rejected.candidateCount();
  }

  /**
   * Returns the NVI points considered valid for this period. For unopened periods, this is the
   * total undisputed points. For open and closed periods, only globally approved points are
   * counted.
   */
  public BigDecimal validPoints() {
    if (period.isUnopened()) {
      return undisputed().totalPoints();
    }
    return forStatus(GlobalApprovalStatus.APPROVED).totalPoints();
  }

  private CandidateTotal undisputed() {
    return byGlobalStatus.entrySet().stream()
        .filter(not(PeriodAggregationResult::isDisputed))
        .map(Map.Entry::getValue)
        .reduce(CandidateTotal.EMPTY_CANDIDATE_TOTAL, CandidateTotal::add);
  }

  private static boolean isDisputed(Map.Entry<GlobalApprovalStatus, CandidateTotal> entry) {
    return entry.getKey() == GlobalApprovalStatus.DISPUTE;
  }
}
