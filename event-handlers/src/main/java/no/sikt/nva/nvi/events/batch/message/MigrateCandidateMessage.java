package no.sikt.nva.nvi.events.batch.message;

import java.util.UUID;
import no.sikt.nva.nvi.common.CandidateMigrationService;

public record MigrateCandidateMessage(UUID candidateIdentifier) implements BatchJobMessage {

  public void execute(CandidateMigrationService candidateMigrationService) {
    candidateMigrationService.migrateCandidate(candidateIdentifier);
  }
}
