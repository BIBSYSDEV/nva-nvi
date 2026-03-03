package no.sikt.nva.nvi.index.report.model;

import java.math.BigDecimal;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;

/**
 * Aggregated NVI candidate data for an entire reporting period. Contains candidate totals grouped
 * by global approval status.
 *
 * @param period the NVI reporting period these results belong to
 * @param byGlobalStatus candidate totals grouped by the candidate's global approval status
 */
public record PeriodAggregationResult(NviPeriod period, GlobalStatusSummary byGlobalStatus) {

  public int countForStatus(GlobalApprovalStatus status) {
    return byGlobalStatus.forStatus(status).candidateCount();
  }

  public int disputedCount() {
    return countForStatus(GlobalApprovalStatus.DISPUTE);
  }

  public int undisputedTotalCount() {
    return byGlobalStatus.undisputed().candidateCount();
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
      return byGlobalStatus.undisputed().totalPoints();
    }
    return byGlobalStatus.forStatus(GlobalApprovalStatus.APPROVED).totalPoints();
  }
}
