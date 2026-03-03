package no.sikt.nva.nvi.index.report.model;

import static java.util.Objects.isNull;

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

  public int countForStatus(GlobalApprovalStatus status) {
    return forStatus(status).candidateCount();
  }

  public int disputedCount() {
    return countForStatus(GlobalApprovalStatus.DISPUTE);
  }

  public int undisputedTotalCount() {
    return byGlobalStatus.entrySet().stream()
        .filter(entry -> entry.getKey() != GlobalApprovalStatus.DISPUTE)
        .map(Map.Entry::getValue)
        .reduce(CandidateTotal.EMPTY_CANDIDATE_TOTAL, CandidateTotal::add)
        .candidateCount();
  }

  public int undisputedProcessedCount() {
    return countForStatus(GlobalApprovalStatus.APPROVED)
        + countForStatus(GlobalApprovalStatus.REJECTED);
  }

  /**
   * Returns the NVI points considered valid for this period. For unopened periods, this is the
   * total undisputed points. For open and closed periods, only globally approved points are
   * counted.
   */
  public BigDecimal validPoints() {
    if (period.isUnopened()) {
      return undisputedTotalPoints();
    }
    return forStatus(GlobalApprovalStatus.APPROVED).totalPoints();
  }

  private BigDecimal undisputedTotalPoints() {
    return byGlobalStatus.entrySet().stream()
        .filter(entry -> entry.getKey() != GlobalApprovalStatus.DISPUTE)
        .map(entry -> entry.getValue().totalPoints())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private CandidateTotal forStatus(GlobalApprovalStatus status) {
    var total = byGlobalStatus.get(status);
    return isNull(total) ? CandidateTotal.EMPTY_CANDIDATE_TOTAL : total;
  }
}
