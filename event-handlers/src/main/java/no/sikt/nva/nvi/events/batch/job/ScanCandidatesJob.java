package no.sikt.nva.nvi.events.batch.job;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.message.MigrateCandidateMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshCandidateMessage;
import no.sikt.nva.nvi.events.batch.request.CandidateScanRequest;

public record ScanCandidatesJob(CandidateService candidateService, CandidateScanRequest request)
    implements BatchJob {

  @Override
  public BatchJobResult execute() {
    var scanResponse = candidateService.listCandidateIdentifiers(request.toScanRequest());
    var nextJob = request.getNextRequest(scanResponse).map(List::of).orElse(emptyList());
    var workItems = toMessages(scanResponse);
    return new BatchJobResult(workItems, nextJob);
  }

  private List<BatchJobMessage> toMessages(ListingResult<UUID> listingResult) {
    return listingResult.getDatabaseEntries().stream().map(this::createMessage).toList();
  }

  private BatchJobMessage createMessage(UUID identifier) {
    return switch (request.jobType()) {
      case REFRESH_CANDIDATES -> new RefreshCandidateMessage(identifier);
      case MIGRATE_CANDIDATES -> new MigrateCandidateMessage(identifier);
      default -> throw new UnsupportedOperationException("Job type not supported");
    };
  }
}
