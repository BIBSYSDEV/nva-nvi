package no.sikt.nva.nvi.events.batch.message;

import java.util.UUID;
import no.sikt.nva.nvi.common.MigrationService;

public record MigrateCandidateMessage(UUID candidateIdentifier) implements BatchJobMessage {

  public void execute(MigrationService migrationService) {
    migrationService.migrateCandidate(candidateIdentifier);
  }
}
