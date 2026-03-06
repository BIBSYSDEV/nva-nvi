package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.service.CandidateService.defaultCandidateService;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.PublicationLoaderService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HandlesMigrationService implements MigrationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(HandlesMigrationService.class);
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";

  private final CandidateService candidateService;
  private final PublicationLoaderService publicationLoader;

  public HandlesMigrationService(
      CandidateService candidateService, StorageReader<URI> storageReader) {
    this.candidateService = candidateService;
    this.publicationLoader = new PublicationLoaderService(storageReader);
  }

  @JacocoGenerated
  public static HandlesMigrationService defaultService() {
    return new HandlesMigrationService(
        defaultCandidateService(),
        new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)));
  }

  @Override
  public void migrateCandidate(UUID identifier) {
    var candidate = candidateService.getCandidateByIdentifier(identifier);

    if (shouldMigrate(candidate)) {
      LOGGER.info("Migrating handles for candidate with identifier {}", identifier);
      migrateHandles(candidate);
    } else {
      LOGGER.info("Candidate {} already has handles, skipping migration", identifier);
    }
  }

  private void migrateHandles(Candidate candidate) {
    var publicationBucketUri = candidate.publicationDetails().publicationBucketUri();
    var publication = publicationLoader.extractAndTransform(publicationBucketUri);
    var updatedDetails =
        candidate.publicationDetails().copy().withHandles(publication.handles()).build();
    var updatedCandidate =
        candidate
            .copy()
            .withPublicationDetails(updatedDetails)
            .withModifiedDate(Instant.now())
            .build();
    candidateService.updateCandidate(updatedCandidate);
  }

  private static boolean shouldMigrate(Candidate candidate) {
    return candidate.publicationDetails().handles().isEmpty();
  }
}
