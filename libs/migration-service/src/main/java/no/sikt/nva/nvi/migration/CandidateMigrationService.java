package no.sikt.nva.nvi.migration;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.service.CandidateService.defaultCandidateService;
import static nva.commons.core.StringUtils.isBlank;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.publication.PublicationLoaderService;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service intended for updating persisted candidates with data from external sources, such as
 * expanded publications stored in S3. This can be used in batch migrations to add missing fields to
 * reported candidates. Currently, backfills missing verified creator names (NP-51445) and missing
 * creator ORCID (NP-51468).
 */
public final class CandidateMigrationService implements MigrationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CandidateMigrationService.class);

  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private final CandidateService candidateService;
  private final PublicationLoaderService publicationLoader;

  public CandidateMigrationService(
      CandidateService candidateService, StorageReader<URI> storageReader) {
    this.candidateService = candidateService;
    this.publicationLoader = new PublicationLoaderService(storageReader);
  }

  @JacocoGenerated
  public static CandidateMigrationService defaultCandidateMigrationService() {
    return new CandidateMigrationService(
        defaultCandidateService(),
        new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)));
  }

  @Override
  public void migrateCandidate(UUID identifier) {
    var candidate = candidateService.getCandidateByIdentifier(identifier);

    if (shouldMigrate(candidate)) {
      LOGGER.info("Migrating candidate with identifier {}", identifier);
      var publicationBucketUri = candidate.publicationDetails().publicationBucketUri();
      var publication = publicationLoader.extractAndTransform(publicationBucketUri);
      var enrichedCandidate = addMissingPublicationDetails(candidate, publication);
      candidateService.updateCandidate(enrichedCandidate);
    } else {
      LOGGER.info("Candidate {} does not require migration", identifier);
      candidateService.updateCandidate(candidate);
    }
  }

  private static boolean shouldMigrate(Candidate candidate) {
    var details = candidate.publicationDetails();
    return details.nviCreators().stream()
        .anyMatch(CandidateMigrationService::hasMissingCreatorData);
  }

  private static Candidate addMissingPublicationDetails(
      Candidate candidate, PublicationDto publication) {
    var currentDetails = candidate.publicationDetails();
    var updatedCreators = addMissingCreatorData(currentDetails.nviCreators(), publication);
    var updatedDetails = currentDetails.copy().withNviCreators(updatedCreators).build();

    return candidate
        .copy()
        .withPublicationDetails(updatedDetails)
        .withModifiedDate(Instant.now())
        .build();
  }

  private static boolean hasMissingCreatorData(NviCreator creator) {
    return shouldUpdateCreatorName(creator) || shouldUpdateCreatorOrcid(creator);
  }

  private static boolean shouldUpdateCreatorName(NviCreator creator) {
    return creator.isVerified() && isBlank(creator.name());
  }

  private static boolean shouldUpdateCreatorOrcid(NviCreator creator) {
    return creator.isVerified() && isNull(creator.orcid());
  }

  private static List<NviCreator> addMissingCreatorData(
      Collection<NviCreator> currentCreators, PublicationDto publication) {
    var creatorNames = buildCreatorNameMap(publication);
    var creatorOrcids = buildCreatorOrcidMap(publication);

    return currentCreators.stream()
        .map(creator -> addMissingCreatorData(creator, creatorNames, creatorOrcids))
        .toList();
  }

  private static Map<URI, String> buildCreatorNameMap(PublicationDto publication) {
    return publication.contributors().stream()
        .filter(ContributorDto::isCreator)
        .filter(ContributorDto::isVerified)
        .filter(not(contributor -> isBlank(contributor.name())))
        .collect(Collectors.toMap(ContributorDto::id, ContributorDto::name));
  }

  private static Map<URI, URI> buildCreatorOrcidMap(PublicationDto publication) {
    return publication.contributors().stream()
        .filter(ContributorDto::isCreator)
        .filter(ContributorDto::isVerified)
        .filter(contributor -> nonNull(contributor.orcid()))
        .collect(Collectors.toMap(ContributorDto::id, ContributorDto::orcid));
  }

  private static NviCreator addMissingCreatorData(
      NviCreator creator, Map<URI, String> creatorNames, Map<URI, URI> creatorOrcids) {
    var updatedCreator = creator.copy();
    if (shouldUpdateCreatorName(creator)) {
      updatedCreator.withName(creatorNames.get(creator.id()));
    }
    if (shouldUpdateCreatorOrcid(creator)) {
      updatedCreator.withOrcid(creatorOrcids.get(creator.id()));
    }
    return updatedCreator.build();
  }
}
