package no.sikt.nva.nvi.common;

import java.util.UUID;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service intended for updating persisted candidates with data from external sources, such as
 * expanded publications stored in S3. This can be used in batch migrations to add missing fields to
 * reported candidates.
 */
public final class CandidateMigrationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CandidateMigrationService.class);

  public CandidateMigrationService() {}

  @JacocoGenerated
  public static CandidateMigrationService defaultCandidateMigrationService() {
    return new CandidateMigrationService();
  }

  public void migrateCandidate(UUID identifier) {
    LOGGER.info("Migrating candidate: {}", identifier);
  }
}
