package no.sikt.nva.nvi.index.report.model;

import static java.util.Objects.isNull;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;

/**
 * Aggregated NVI candidate data for a single institution within a reporting period. Contains
 * candidate totals grouped by global approval status and a pre-computed undisputed summary.
 *
 * <p>Candidate data is aggregated by the combination of global and local approval status. Local
 * approval status refers to the approval from this specific institution, while global approval
 * status is derived from the approvals from all involved institutions.
 *
 * @param institutionId the URI identifying the institution
 * @param period the NVI reporting period these results belong to
 * @param sector the institution's sector classification
 * @param labels display names for the institution keyed by language code
 * @param byGlobalStatus candidate totals grouped by the candidate's global approval status
 * @param undisputed pre-computed summary of all candidates that are not in dispute
 */
public record InstitutionAggregationResult(
    URI institutionId,
    NviPeriod period,
    Sector sector,
    Map<String, String> labels,
    Map<GlobalApprovalStatus, LocalStatusSummary> byGlobalStatus,
    LocalStatusSummary undisputed) {

  /** Returns the total number of candidates that are in dispute. */
  public int disputedCount() {
    var disputeSummary = byGlobalStatus.get(GlobalApprovalStatus.DISPUTE);
    return isNull(disputeSummary) ? 0 : disputeSummary.totalCount();
  }

  /**
   * Returns the NVI points considered valid for this institution. For unopened periods, this is the
   * total undisputed points. For open and closed periods, only globally approved points are
   * counted.
   *
   * <p>TODO: Return reported points in "reported periods" when reporting period cycle is
   * implemented (NP-49541)
   */
  public BigDecimal validPoints() {
    if (period.isUnopened()) {
      return undisputed.totalPoints();
    }
    return approvedPoints();
  }

  /** Returns the total number of undisputed candidates across all local statuses. */
  public int undisputedTotalCount() {
    return undisputed.totalCount();
  }

  /** Returns the number of undisputed candidates that have been locally approved or rejected. */
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
