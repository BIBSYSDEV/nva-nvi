package no.sikt.nva.nvi.common;

import java.util.UUID;

public interface CandidateMigrationService {

  void migrateCandidate(UUID identifier);
}
