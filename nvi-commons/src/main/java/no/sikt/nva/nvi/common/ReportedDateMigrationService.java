package no.sikt.nva.nvi.common;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.service.CandidateService.defaultCandidateService;

import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportedDateMigrationService implements MigrationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReportedDateMigrationService.class);
  private final CandidateService candidateService;

  public ReportedDateMigrationService(CandidateService candidateService) {
    this.candidateService = candidateService;
  }

  @JacocoGenerated
  public static ReportedDateMigrationService defaultService() {
    return new ReportedDateMigrationService(defaultCandidateService());
  }

  @Override
  public void migrateCandidate(UUID identifier) {
    var candidate = candidateService.getCandidateByIdentifier(identifier);
    if (shouldMigrate(candidate)) {
      LOGGER.info(
          "Setting reportedDate for candidate {} (year={}) to {}",
          identifier,
          candidate.period().publishingYear(),
          candidate.period().reportingDate());
      var updatedCandidate = backfillReportedDate(candidate);
      candidateService.updateCandidate(updatedCandidate);
    }
  }

  private static boolean shouldMigrate(Candidate candidate) {
    return candidate.isReported() && isNull(candidate.reportedDate());
  }

  private static Candidate backfillReportedDate(Candidate candidate) {
    return candidate
        .copy()
        .withReportedDate(candidate.period().reportingDate())
        .withModifiedDate(Instant.now())
        .build();
  }
}
