package no.sikt.nva.nvi.common.utils;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.model.DbPages;
import no.sikt.nva.nvi.common.db.model.DbPointCalculation;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.db.model.DbPublicationDetails;
import no.sikt.nva.nvi.common.db.model.KeyField;
import no.sikt.nva.nvi.common.dto.PageCountDto;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.service.PublicationLoaderService;
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
      return migratePublicationField(storedCandidate);
    }
    return databaseEntry;
  }

  /**
   * @deprecated Temporary migration code. To be removed when all candidates have been migrated.
   *     This migration creates a new "DbPublication" object as a sub-field on the CandidateDao,
   *     which contains both new data (that we did not persist before) and old data (that we want to
   *     move to the new object).
   */
  @Deprecated(forRemoval = true, since = "2025-04-29")
  private CandidateDao migratePublicationField(CandidateDao candidateDao) {
    var dbCandidate = candidateDao.candidate();
    //      // TODO: Add publication identifier as top-level field
    //      // TODO: Add @Deprecated annotation to the fields we can remove
    if (isNull(dbCandidate.publicationDetails())) {
      var publicationBucketUri = dbCandidate.publicationBucketUri();
      var publication = publicationLoader.extractAndTransform(publicationBucketUri);

      // Build the new structure for persisted publication metadata from new and old data
      var dbPublicationChannel =
          DbPublicationChannel.builder()
              .id(dbCandidate.channelId())
              .channelType(dbCandidate.channelType())
              .scientificValue(dbCandidate.level().getValue())
              .build();
      var dbTopLevelOrganizations =
          publication.topLevelOrganizations().stream().map(Organization::toDbOrganization).toList();
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

      var dbPublicationDetails =
          DbPublicationDetails.builder()
              // Get data we know should exist already from data stored in the database
              .id(dbCandidate.publicationId())
              .publicationBucketUri(dbCandidate.publicationBucketUri())
              .publicationDate(dbCandidate.publicationDate())
              .creators(dbCandidate.creators())

              // Get other data from the parsed S3 document
              .identifier(publication.identifier())
              .title(publication.title())
              .status(publication.status())
              .modifiedDate(publication.modifiedDate())
              .contributorCount(publication.contributors().size())
              .abstractText(publication.abstractText())
              .pages(dbPagesFromDto(publication.pageCount()))
              .topLevelOrganizations(dbTopLevelOrganizations)
              .build();

      var updatedData =
          dbCandidate
              .copy()
              .publicationIdentifier(publication.identifier())
              .pointCalculation(dbPointCalculation)
              .publicationDetails(dbPublicationDetails)
              .build();
      return candidateDao.copy().candidate(updatedData).build();
    }
    return candidateDao;
  }

  private static DbPages dbPagesFromDto(PageCountDto dtoPages) {
    if (isNull(dtoPages)) {
      return null;
    }
    return DbPages.builder()
        .firstPage(dtoPages.firstPage())
        .lastPage(dtoPages.lastPage())
        .numberOfPages(dtoPages.numberOfPages())
        .build();
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
