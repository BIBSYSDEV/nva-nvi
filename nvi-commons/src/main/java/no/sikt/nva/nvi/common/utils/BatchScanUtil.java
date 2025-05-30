package no.sikt.nva.nvi.common.utils;

import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.StringUtils.isBlank;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import no.sikt.nva.nvi.common.db.model.DbPointCalculation;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.db.model.DbPublicationDetails;
import no.sikt.nva.nvi.common.db.model.KeyField;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.service.PublicationLoaderService;
import no.sikt.nva.nvi.common.service.model.PageCount;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class BatchScanUtil {

  private final CandidateRepository candidateRepository;
  private final PublicationLoaderService publicationLoader;

  public BatchScanUtil(CandidateRepository candidateRepository, StorageReader<URI> storageReader) {
    this.candidateRepository = candidateRepository;
    this.publicationLoader = new PublicationLoaderService(storageReader);
  }

  @JacocoGenerated
  public static BatchScanUtil defaultNviService() {
    return new BatchScanUtil(
        new CandidateRepository(defaultDynamoClient()),
        new S3StorageReader(new Environment().readEnv("EXPANDED_RESOURCES_BUCKET")));
  }

  public ListingResult<Dao> migrateAndUpdateVersion(
      int pageSize, Map<String, String> startMarker, List<KeyField> types) {
    var scanResult = candidateRepository.scanEntries(pageSize, startMarker, types);
    var entries = migrate(scanResult.getDatabaseEntries());
    candidateRepository.writeEntries(entries);
    return scanResult;
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
      return migrateCandidateDao(storedCandidate);
    }
    return databaseEntry;
  }

  private CandidateDao migrateCandidateDao(CandidateDao candidateDao) {
    // Check which migrations we need to run
    var dbCandidate = candidateDao.candidate();
    var shouldMigratePublicationDetails =
        Optional.ofNullable(dbCandidate.publicationDetails()).isEmpty();
    var shouldMigrateTopLevelOrganizations =
        Optional.ofNullable(dbCandidate.publicationDetails())
            .map(DbPublicationDetails::topLevelNviOrganizations)
            .filter(not(List::isEmpty))
            .isEmpty();

    // Skip migration if both fields are already populated
    if (!shouldMigratePublicationDetails && !shouldMigrateTopLevelOrganizations) {
      return candidateDao;
    }

    // Get the parsed publication metadata
    var publicationBucketUri = dbCandidate.publicationBucketUri();
    var publication = publicationLoader.extractAndTransform(publicationBucketUri);

    if (shouldMigratePublicationDetails) {
      dbCandidate = migratePublicationField(dbCandidate, publication);
    }

    if (shouldMigrateTopLevelOrganizations) {
      dbCandidate = migrateTopLevelNviOrganizations(dbCandidate, publication);
    }

    return candidateDao.copy().candidate(dbCandidate).build();
  }

  /**
   * @deprecated Temporary migration code. To be removed when all candidates have been migrated.
   *     This migration creates a new "DbPublication" object as a sub-field on the CandidateDao,
   *     which contains both new data (that we did not persist before) and old data (that we want to
   *     move to the new object).
   */
  @Deprecated(forRemoval = true, since = "2025-04-29")
  private DbCandidate migratePublicationField(DbCandidate dbCandidate, PublicationDto publication) {
    // Build the new structure for persisted publication metadata from new and old data
    var dbPointCalculation = getMigratedPointCalculation(dbCandidate);
    var updatedDbCreators = getMigratedCreators(dbCandidate.creators(), publication);

    var dbPublicationDetails =
        DbPublicationDetails.builder()
            // Get data we know should exist already from data stored in the database
            .id(dbCandidate.publicationId())
            .publicationBucketUri(dbCandidate.publicationBucketUri())
            .publicationDate(dbCandidate.publicationDate())

            // Get other data from the parsed S3 document
            .creators(updatedDbCreators)
            .identifier(publication.identifier())
            .title(publication.title())
            .status(publication.status())
            .modifiedDate(publication.modifiedDate())
            .contributorCount(publication.contributors().size())
            .abstractText(publication.abstractText())
            .pages(PageCount.from(publication.pageCount()).toDbPageCount())
            .build();

    return dbCandidate
        .copy()
        .publicationIdentifier(publication.identifier())
        .pointCalculation(dbPointCalculation)
        .publicationDetails(dbPublicationDetails)
        .creators(updatedDbCreators)
        .build();
  }

  /**
   * @deprecated Temporary migration code. To be removed when all candidates have been migrated.
   *     This migration populates the "topLevelOrganizations" field based on:
   *     <ul>
   *       <li>The organization hierarchy from parsing the original expanded publication in S3
   *       <li>The existing affiliations of persisted creators
   *     </ul>
   *     It also populates the 'language' field, which was missed in the previous migration.
   */
  @Deprecated(forRemoval = true, since = "2025-05-15")
  private DbCandidate migrateTopLevelNviOrganizations(
      DbCandidate dbCandidate, PublicationDto publication) {

    var currentAffiliations =
        dbCandidate.creators().stream()
            .map(DbCreatorType::affiliations)
            .flatMap(List::stream)
            .distinct()
            .toList();

    var newTopLevelOrganizations =
        publication.topLevelOrganizations().stream()
            .filter(isTopLevelOrganizationOfAny(currentAffiliations))
            .map(Organization::toDbOrganization)
            .distinct()
            .toList();

    var originalDetails = dbCandidate.publicationDetails();
    var updatedDetails =
        DbPublicationDetails.builder()
            // Copy existing data
            .id(originalDetails.id())
            .publicationBucketUri(originalDetails.publicationBucketUri())
            .pages(originalDetails.pages())
            .publicationDate(originalDetails.publicationDate())
            .creators(originalDetails.creators())
            .modifiedDate(originalDetails.modifiedDate())
            .contributorCount(originalDetails.contributorCount())
            .abstractText(originalDetails.abstractText())
            .identifier(originalDetails.identifier())
            .status(originalDetails.status())
            .title(originalDetails.title())

            // Add new data from the parsed S3 document
            .topLevelNviOrganizations(newTopLevelOrganizations)
            .language(publication.language())
            .build();

    return dbCandidate.copy().publicationDetails(updatedDetails).build();
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
    var dbPointCalculation =
        DbPointCalculation.builder()
            .basePoints(dbCandidate.basePoints())
            .collaborationFactor(dbCandidate.collaborationFactor())
            .totalPoints(dbCandidate.totalPoints())
            .publicationChannel(dbPublicationChannel)
            .institutionPoints(dbCandidate.points())
            .internationalCollaboration(dbCandidate.internationalCollaboration())
            .creatorShareCount(dbCandidate.creatorShareCount())
            .instanceType(dbCandidate.instanceType())
            .build();
    return dbPointCalculation;
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
