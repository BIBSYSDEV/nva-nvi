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
    candidateRepository.writeEntries(scanResult.getDatabaseEntries());
    return scanResult;
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
