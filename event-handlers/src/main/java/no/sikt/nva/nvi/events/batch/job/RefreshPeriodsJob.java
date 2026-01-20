package no.sikt.nva.nvi.events.batch.job;

import static java.util.Objects.nonNull;

import java.util.List;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshPeriodMessage;
import no.sikt.nva.nvi.events.batch.model.BatchJobFilter;
import no.sikt.nva.nvi.events.batch.model.ReportingYearFilter;

public record RefreshPeriodsJob(
    NviPeriodService periodService, Integer maxItems, BatchJobFilter filter) implements BatchJob {

  @Override
  public BatchJobResult execute() {
    var messages =
        periodService.getAll().stream()
            .map(NviPeriod::publishingYear)
            .map(String::valueOf)
            .filter(this::isAllowedByFilter)
            .limit(getEffectiveLimit())
            .map(RefreshPeriodMessage::new)
            .map(BatchJobMessage.class::cast)
            .toList();
    return BatchJobResult.createTerminalBatchJobResult(messages);
  }

  private int getEffectiveLimit() {
    return nonNull(maxItems) ? maxItems : Integer.MAX_VALUE;
  }

  // TODO: Move to filter class and re-use?
  private boolean isAllowedByFilter(String year) {
    if (filter instanceof ReportingYearFilter(List<String> reportingYears)) {
      return reportingYears.contains(year);
    }
    return true;
  }
}
