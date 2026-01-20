package no.sikt.nva.nvi.events.batch.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.TableScanRequest;
import no.sikt.nva.nvi.common.model.ListingResult;

public record CandidateScanRequest(
    BatchJobType jobType, int segment, int totalSegments, PaginationState paginationState)
    implements BatchJobRequest {

  @JsonIgnore
  public int batchSize() {
    return paginationState().batchSize();
  }

  @JsonIgnore
  public TableScanRequest toScanRequest() {
    return new TableScanRequest(
        segment, totalSegments, paginationState.batchSize(), paginationState.lastCandidateRead());
  }

  @JsonIgnore
  public Optional<CandidateScanRequest> getNextRequest(ListingResult<UUID> scanResult) {
    var updatedPaginationState = paginationState.createUpdatedPaginationState(scanResult);
    if (updatedPaginationState.isTerminalState()) {
      return Optional.empty();
    }
    return Optional.of(
        new CandidateScanRequest(jobType, segment, totalSegments, updatedPaginationState));
  }
}
