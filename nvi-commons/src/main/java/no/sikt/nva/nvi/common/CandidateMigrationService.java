package no.sikt.nva.nvi.common;

import static java.util.function.Predicate.not;
import static nva.commons.core.StringUtils.isBlank;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.PublicationLoaderService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service intended for updating persisted candidates with data from external sources, such as
 * expanded publications stored in S3. This can be used in batch migrations to add missing fields to
 * reported candidates.
 */
public class CandidateMigrationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CandidateMigrationService.class);

  private final CandidateService candidateService;
  private final PublicationLoaderService publicationLoader;

  public CandidateMigrationService(
      CandidateService candidateService, StorageReader<URI> storageReader) {
    this.candidateService = candidateService;
    this.publicationLoader = new PublicationLoaderService(storageReader);
  }

  public void migrateCandidate(UUID identifier) {
    LOGGER.info("Migrating candidate with identifier {}", identifier);
    var candidate = candidateService.getCandidateByIdentifier(identifier);

    if (!shouldMigrate(candidate)) {
      LOGGER.info("Candidate {} does not require migration", identifier);
      return;
    }

    var publicationBucketUri = candidate.publicationDetails().publicationBucketUri();
    var publication = publicationLoader.extractAndTransform(publicationBucketUri);
    var enrichedCandidate = addMissingPublicationDetails(candidate, publication);
    candidateService.updateCandidate(enrichedCandidate);
  }

  private static boolean shouldMigrate(Candidate candidate) {
    var details = candidate.publicationDetails();
    return hasCreatorsWithMissingNames(details.nviCreators());
  }

  private static Candidate addMissingPublicationDetails(
      Candidate candidate, PublicationDto publication) {
    var currentDetails = candidate.publicationDetails();
    var updatedCreators = addMissingCreatorNames(currentDetails.nviCreators(), publication);
    var updatedDetails = currentDetails.copy().withNviCreators(updatedCreators).build();

    return candidate
        .copy()
        .withPublicationDetails(updatedDetails)
        .withModifiedDate(Instant.now())
        .build();
  }

  private static boolean hasCreatorsWithMissingNames(Collection<NviCreator> creators) {
    return creators.stream()
        .filter(CandidateMigrationService::shouldUpdateCreatorName)
        .findAny()
        .isPresent();
  }

  private static boolean shouldUpdateCreatorName(NviCreator creator) {
    return creator.isVerified() && isBlank(creator.name());
  }

  private static List<NviCreator> addMissingCreatorNames(
      Collection<NviCreator> currentCreators, PublicationDto publication) {
    var creatorNames = buildCreatorNameMap(publication);

    return currentCreators.stream()
        .map(creator -> addNameToCreatorIfMissing(creator, creatorNames))
        .toList();
  }

  private static Map<URI, String> buildCreatorNameMap(PublicationDto publication) {
    return publication.contributors().stream()
        .filter(ContributorDto::isCreator)
        .filter(ContributorDto::isVerified)
        .filter(not(contributor -> isBlank(contributor.name())))
        .collect(Collectors.toMap(ContributorDto::id, ContributorDto::name));
  }

  private static NviCreator addNameToCreatorIfMissing(
      NviCreator creator, Map<URI, String> creatorNamesFromPublication) {
    var creatorName =
        shouldUpdateCreatorName(creator)
            ? creatorNamesFromPublication.get(creator.id())
            : creator.name();
    return new NviCreator(
        creator.id(),
        creatorName,
        creator.verificationStatus(),
        creator.nviAffiliations(),
        creator.topLevelNviOrganizations());
  }
}
