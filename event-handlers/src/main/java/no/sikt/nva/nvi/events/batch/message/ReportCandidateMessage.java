package no.sikt.nva.nvi.events.batch.message;

import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.service.CandidateService;

public record ReportCandidateMessage(UUID candidateIdentifier) implements BatchJobMessage {

  public void execute(CandidateService candidateService) {
    candidateService.reportCandidate(candidateIdentifier, Instant.now());
  }
}
