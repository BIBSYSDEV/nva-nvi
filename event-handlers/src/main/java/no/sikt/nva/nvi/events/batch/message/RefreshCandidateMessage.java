package no.sikt.nva.nvi.events.batch.message;

import java.util.UUID;
import no.sikt.nva.nvi.common.service.CandidateService;

public record RefreshCandidateMessage(UUID candidateIdentifier) implements BatchJobMessage {

  public void execute(CandidateService candidateService) {
    candidateService.refreshCandidate(candidateIdentifier);
  }
}
