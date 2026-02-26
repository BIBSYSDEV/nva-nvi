package no.sikt.nva.nvi.index.report.model;

import static java.util.Objects.isNull;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;

public record InstitutionAggregationResult(
    URI institutionId,
    NviPeriod period,
    String sector,
    CandidateTotal reportedTotals,
    Map<GlobalApprovalStatus, LocalStatusSummary> byGlobalStatus,
    LocalStatusSummary undisputed) {

  public int disputedCount() {
    var disputeSummary = byGlobalStatus.get(GlobalApprovalStatus.DISPUTE);
    return isNull(disputeSummary) ? 0 : disputeSummary.totalCount();
  }

  /**
   * Returns the total points that should be counted for this institution, depending on the period
   * state:
   *
   * <ul>
   *   <li>Unopened – all undisputed points
   *   <li>Open – only globally approved points
   *   <li>Closed – only explicitly reported points
   * </ul>
   *
   * // TODO: Update this when period state is implemented: // - Open and closed state => globally
   * approved points // - New "reported" state => all reported points
   */
  public BigDecimal validPoints() {
    // FIXME: Replace with switch case when period state is implemented
    if (period.isUnopened()) {
      return undisputed.totalPoints();
    }
    if (period.isClosed() || period.isOpen()) {
      return approvedPoints();
    }
    return reportedTotals().totalPoints();
  }

  public int undisputedTotalCount() {
    return undisputed.totalCount();
  }

  public int undisputedProcessedCount() {
    var approved = undisputed.forStatus(ApprovalStatus.APPROVED).candidateCount();
    var rejected = undisputed.forStatus(ApprovalStatus.REJECTED).candidateCount();
    return approved + rejected;
  }

  private BigDecimal approvedPoints() {
    var approvedSummary = byGlobalStatus.get(GlobalApprovalStatus.APPROVED);
    return isNull(approvedSummary) ? BigDecimal.ZERO : approvedSummary.totalPoints();
  }
}
