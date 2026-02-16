package no.sikt.nva.nvi.common;

import java.util.UUID;

@FunctionalInterface
public interface MigrationService {

  void migrateCandidate(UUID identifier);
}
