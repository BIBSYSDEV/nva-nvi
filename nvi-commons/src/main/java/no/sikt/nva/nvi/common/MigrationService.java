package no.sikt.nva.nvi.common;

import java.util.UUID;

public interface MigrationService {

  void migrateCandidate(UUID identifier);
}
