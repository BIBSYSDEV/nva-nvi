package no.sikt.nva.nvi.common.utils;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.StringUtils.isBlank;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.model.DbOrganization;
import no.sikt.nva.nvi.common.db.model.DbPointCalculation;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.db.model.DbPublicationDetails;
import no.sikt.nva.nvi.common.db.model.KeyField;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.PublicationLoaderService;
import no.sikt.nva.nvi.common.service.model.PageCount;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

@SuppressWarnings("PMD.CouplingBetweenObjects")
public class BatchScanUtil {

  private static final String BATCH_SCAN_RECOVERY_QUEUE = "BATCH_SCAN_RECOVERY_QUEUE";
  private final CandidateRepository candidateRepository;
  private final PublicationLoaderService publicationLoader;
  private final QueueClient queueClient;
  private final Environment environment;

  public BatchScanUtil(
      CandidateRepository candidateRepository,
      StorageReader<URI> storageReader,
      QueueClient queueClient,
      Environment environment) {
    this.candidateRepository = candidateRepository;
    this.publicationLoader = new PublicationLoaderService(storageReader);
    this.queueClient = queueClient;
    this.environment = environment;
  }

  @JacocoGenerated
  public static BatchScanUtil defaultNviService() {
    return new BatchScanUtil(
        new CandidateRepository(defaultDynamoClient()),
        new S3StorageReader(new Environment().readEnv("EXPANDED_RESOURCES_BUCKET")),
        new NviQueueClient(),
        new Environment());
  }

  public ListingResult<Dao> migrateAndUpdateVersion(
      int pageSize, Map<String, String> startMarker, List<KeyField> types) {
    var scanResult = candidateRepository.scanEntries(pageSize, startMarker, types);
    var entries = migrate(scanResult.getDatabaseEntries());
    candidateRepository.writeEntries(entries);
    return scanResult;
  }

  public void migrateAndUpdateVersion(Collection<UUID> candidateIdentifiers) {
    var migratedCandidates =
        candidateIdentifiers.stream()
            .map(candidateRepository::findCandidateById)
            .flatMap(Optional::stream)
            .map(this::migrate)
            .toList();
    candidateRepository.writeEntries(migratedCandidates);
  }

  /**
   * This is a wrapper method for any temporary migration code that needs to be added. Ensure that
   * all methods are idempotent and have deprecation annotations.
   */
  private List<Dao> migrate(List<Dao> databaseEntries) {
    return databaseEntries.stream().map(this::migrate).toList();
  }

  private Dao migrate(Dao databaseEntry) {
    if (databaseEntry instanceof CandidateDao storedCandidate) {
      return attemptToMigrateCandidate(storedCandidate);
    }
    return databaseEntry;
  }

  private Dao attemptToMigrateCandidate(CandidateDao candidateDao) {
    try {
      return migrateCandidateDao(candidateDao);
    } catch (Exception e) {
      queueClient.sendMessage(
          e.toString(), environment.readEnv(BATCH_SCAN_RECOVERY_QUEUE), candidateDao.identifier());
      return candidateDao;
    }
  }

  private CandidateDao migrateCandidateDao(CandidateDao candidateDao) {
    var dbCandidate = candidateDao.candidate();
    var dbPublicationDetails = dbCandidate.publicationDetails();
    var shouldMigratePublicationDetails = shouldMigratePublicationDetails(dbPublicationDetails);

    if (!shouldMigratePublicationDetails) {
      return candidateDao;
    }

    // Get the parsed publication metadata and add missing data
    var publicationBucketUri = dbCandidate.publicationBucketUri();
    var publication = publicationLoader.extractAndTransform(publicationBucketUri);
    var updatedCandidate = migratePublicationDetails(dbCandidate, publication);
    return candidateDao.copy().candidate(updatedCandidate).build();
  }

  private boolean shouldMigratePublicationDetails(DbPublicationDetails publicationDetails) {
    return isNull(publicationDetails) || publicationDetails.hasNullOrEmptyValues();
  }

  /**
   * @deprecated Temporary migration code. To be removed when all candidates have been migrated.
   *     This migration creates a new "DbPublication" object as a sub-field on the CandidateDao,
   *     which contains both new data (that we did not persist before) and old data (that we want to
   *     move to the new object).
   */
  @Deprecated(forRemoval = true, since = "2025-04-29")
  private DbCandidate migratePublicationDetails(
      DbCandidate dbCandidate, PublicationDto publication) {
    // Build the new structure for persisted publication metadata from new and old data
    var originalDetails = dbCandidate.publicationDetails();
    var dbPointCalculation = getMigratedPointCalculation(dbCandidate);
    var updatedDbCreators = getMigratedCreators(dbCandidate.creators(), publication);
    var updatedOrganizations = getRelevantTopLevelOrganizations(updatedDbCreators, publication);

    var dbPublicationDetails =
        DbPublicationDetails.builder()
            // Get data we know should exist already from data stored in the database
            .id(dbCandidate.publicationId())
            .publicationBucketUri(dbCandidate.publicationBucketUri())
            .publicationDate(dbCandidate.publicationDate())
            .contributorCount(updatedDbCreators.size())

            // Always update these
            .topLevelNviOrganizations(updatedOrganizations) // Used for access control
            .creators(updatedDbCreators) // Only adding missing names

            // Update other fields only if missing
            .identifier(
                getFieldOrDefault(
                    originalDetails, DbPublicationDetails::identifier, publication.identifier()))
            .title(
                getFieldOrDefault(
                    originalDetails, DbPublicationDetails::title, publication.title()))
            .status(
                getFieldOrDefault(
                    originalDetails, DbPublicationDetails::status, publication.status()))
            .modifiedDate(
                getFieldOrDefault(
                    originalDetails,
                    DbPublicationDetails::modifiedDate,
                    publication.modifiedDate()))
            .abstractText(
                getFieldOrDefault(
                    originalDetails,
                    DbPublicationDetails::abstractText,
                    publication.abstractText()))
            .language(
                getFieldOrDefault(
                    originalDetails, DbPublicationDetails::language, publication.language()))
            .pages(
                getFieldOrDefault(
                    originalDetails,
                    DbPublicationDetails::pages,
                    PageCount.from(publication.pageCount()).toDbPageCount()))
            .build();

    return dbCandidate
        .copy()
        .publicationIdentifier(publication.identifier())
        .pointCalculation(dbPointCalculation)
        .publicationDetails(dbPublicationDetails)
        .creators(updatedDbCreators)
        .build();
  }

  private <T, R> T getFieldOrDefault(R object, Function<R, T> getter, T defaultValue) {
    return Optional.ofNullable(object).map(getter).filter(Objects::nonNull).orElse(defaultValue);
  }

  private List<DbOrganization> getRelevantTopLevelOrganizations(
      Collection<DbCreatorType> creators, PublicationDto publication) {
    var currentAffiliations =
        creators.stream()
            .map(DbCreatorType::affiliations)
            .flatMap(List::stream)
            .distinct()
            .toList();

    return publication.topLevelOrganizations().stream()
        .filter(isTopLevelOrganizationOfAny(currentAffiliations))
        .map(Organization::toDbOrganization)
        .distinct()
        .toList();
  }

  private static Predicate<Organization> isTopLevelOrganizationOfAny(Collection<URI> affiliations) {
    return topLevelOrganization -> {
      var candidateOrganizations = new ArrayDeque<>(List.of(topLevelOrganization));
      while (!candidateOrganizations.isEmpty()) {
        var organization = candidateOrganizations.pop();
        if (affiliations.contains(organization.id())) {
          return true;
        }
        if (nonNull(organization.hasPart()) && !organization.hasPart().isEmpty()) {
          candidateOrganizations.addAll(organization.hasPart());
        }
      }
      return false;
    };
  }

  /**
   * Migrates existing point calculation data to a new structure.
   *
   * @param dbCandidate the DbCandidate object containing the existing point calculation data.
   * @return a new DbPointCalculation object with existing data restructured.
   */
  private static DbPointCalculation getMigratedPointCalculation(DbCandidate dbCandidate) {
    var dbPublicationChannel =
        DbPublicationChannel.builder()
            .id(dbCandidate.channelId())
            .channelType(dbCandidate.channelType())
            .scientificValue(dbCandidate.level().getValue())
            .build();
    return DbPointCalculation.builder()
        .basePoints(dbCandidate.basePoints())
        .collaborationFactor(dbCandidate.collaborationFactor())
        .totalPoints(dbCandidate.totalPoints())
        .publicationChannel(dbPublicationChannel)
        .institutionPoints(dbCandidate.points())
        .internationalCollaboration(dbCandidate.internationalCollaboration())
        .creatorShareCount(dbCandidate.creatorShareCount())
        .instanceType(dbCandidate.instanceType())
        .build();
  }

  /**
   * Migrates existing creator data and adds names to verified creators.
   *
   * @param dbCreators Verified and unverified creators from the database.
   * @param publication Parsed publication data from S3 with all contributor data.
   * @return List of DbCreatorType objects with updated names for verified creators.
   */
  private List<DbCreatorType> getMigratedCreators(
      Collection<DbCreatorType> dbCreators, PublicationDto publication) {
    var creatorNames =
        publication.contributors().stream()
            .filter(ContributorDto::isCreator)
            .filter(ContributorDto::isVerified)
            .filter(not(contributor -> isBlank(contributor.name())))
            .map(creator -> Map.entry(creator.id(), creator.name()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    return dbCreators.stream().map(dbCreator -> migrateDbCreator(dbCreator, creatorNames)).toList();
  }

  private DbCreatorType migrateDbCreator(DbCreatorType dbCreator, Map<URI, String> creatorNames) {
    if (dbCreator instanceof DbCreator verifiedDbCreator) {
      var creatorName = creatorNames.get(verifiedDbCreator.creatorId());
      return new DbCreator(
          verifiedDbCreator.creatorId(), creatorName, verifiedDbCreator.affiliations());
    }
    return dbCreator;
  }

  public ListingResult<CandidateDao> fetchCandidatesByYear(
      String year,
      boolean includeReportedCandidates,
      Integer pageSize,
      Map<String, String> startMarker) {
    return candidateRepository.fetchCandidatesByYear(
        year, includeReportedCandidates, pageSize, startMarker);
  }
}
