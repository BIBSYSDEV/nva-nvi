package no.sikt.nva.nvi.events.batch.message;

import no.sikt.nva.nvi.common.service.NviPeriodService;

public record RefreshPeriodMessage(String year) implements BatchJobMessage {

  public void execute(NviPeriodService periodService) {
    periodService.refreshPeriod(year);
  }
}
