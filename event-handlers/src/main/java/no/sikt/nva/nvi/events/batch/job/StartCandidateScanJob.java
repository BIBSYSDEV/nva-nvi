package no.sikt.nva.nvi.events.batch.job;

import static no.sikt.nva.nvi.common.utils.CollectionUtils.splitEvenly;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.events.batch.request.CandidateScanRequest;
import no.sikt.nva.nvi.events.batch.request.PaginationState;
import no.sikt.nva.nvi.events.batch.request.StartBatchJobRequest;

public record StartCandidateScanJob(StartBatchJobRequest request) implements BatchJob {

  @Override
  public BatchJobResult execute() {
    var jobs = createInitialTableScanEvents(request);
    return BatchJobResult.createInitialBatchJobResult(jobs);
  }

  private List<CandidateScanRequest> createInitialTableScanEvents(
      StartBatchJobRequest request) {
    var segmentLimits = getSegmentLimits(request);

    var totalSegments = request.maxParallelSegments();
    return IntStream.range(0, totalSegments)
        .mapToObj(
            segment -> createParallelScanRequest(request, segment, segmentLimits.get(segment)))
        .toList();
  }

  private static CandidateScanRequest createParallelScanRequest(
      StartBatchJobRequest request, int segment, Integer maxItems) {
    var paginationState =
        PaginationState.createInitialPaginationState(request.batchSize(), maxItems);
    return new CandidateScanRequest(
        request.jobType(), segment, request.maxParallelSegments(), paginationState);
  }

  private static List<Integer> getSegmentLimits(StartBatchJobRequest request) {
    if (request.hasItemLimit()) {
      return splitEvenly(request.maxItems(), request.maxParallelSegments());
    }
    return Stream.generate(() -> (Integer) null).limit(request.maxParallelSegments()).toList();
  }
}
