package no.sikt.nva.nvi.index.report.model;

import static java.util.Objects.isNull;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;

public record InstitutionAggregationResult(
    URI institutionId,
    NviPeriod period,
    Sector sector,
    Map<String, String> labels,
    Map<GlobalApprovalStatus, LocalStatusSummary> byGlobalStatus,
    LocalStatusSummary undisputed) {

  public int disputedCount() {
    var disputeSummary = byGlobalStatus.get(GlobalApprovalStatus.DISPUTE);
    return isNull(disputeSummary) ? 0 : disputeSummary.totalCount();
  }

  public BigDecimal validPoints() {
    if (period.isUnopened()) {
      return undisputed.totalPoints();
    }
    return approvedPoints();
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
