package no.sikt.nva.nvi.migration;

import java.util.UUID;

@FunctionalInterface
public interface MigrationService {

  void migrateCandidate(UUID identifier);
}
