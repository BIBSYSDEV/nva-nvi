package no.sikt.nva.nvi.common.utils;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;

import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.model.KeyField;
import no.sikt.nva.nvi.common.model.ListingResult;
import nva.commons.core.JacocoGenerated;

public class BatchScanUtil {

  private final CandidateRepository candidateRepository;

  public BatchScanUtil(CandidateRepository candidateRepository) {
    this.candidateRepository = candidateRepository;
  }

  @JacocoGenerated
  public static BatchScanUtil defaultNviService() {
    return new BatchScanUtil(new CandidateRepository(defaultDynamoClient()));
  }

  public ListingResult<Dao> migrateAndUpdateVersion(
      int pageSize, Map<String, String> startMarker, List<KeyField> types) {
    var scanResult = candidateRepository.scanEntries(pageSize, startMarker, types);
    // DO **MAGIC** STUFF HERE
    var entries = migrate(scanResult.getDatabaseEntries());
    candidateRepository.writeEntries(entries);
    return scanResult;
  }

  // ONLY IDEMPOTENT STUFF HERE
  // TODO: Write test for this
  // TODO: Keep this and add a comment
  private List<Dao> migrate(List<Dao> databaseEntries) {
    // FOR EACH:
    return databaseEntries.stream().map(this::migrate).toList();
  }

  private Dao migrate(Dao databaseEntry) {
    if (databaseEntry instanceof CandidateDao storedCandidate) {
      return migratePublicationField(storedCandidate);
    }
    return databaseEntry;
  }

  /** // TODO: Add deprecated annotation to this method */
  @Deprecated
  private CandidateDao migratePublicationField(CandidateDao entry) {
    var data = entry.candidate();
    //    if (isNull(data.publicationDetails())) {
    //      // TODO: Parse from S3 and add publication data as entity
    //      // TODO: Add entire publication details as new field
    //      // TODO: Add publication identifier as top-level field
    //      // TODO: Add @Deprecated annotation to the fields we can remove
    //      var updatedData = data.copy().build();
    //      return entry.copy().candidate(updatedData).build();
    //    }
    return entry;
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
