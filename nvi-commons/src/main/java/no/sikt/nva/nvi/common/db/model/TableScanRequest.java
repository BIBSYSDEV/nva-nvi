package no.sikt.nva.nvi.common.db.model;

import java.util.Map;
import no.sikt.nva.nvi.common.db.request.CandidateScanRequest;

public record TableScanRequest(
    int segment, int totalSegments, int batchSize, Map<String, String> lastItemRead)
    implements CandidateScanRequest {}
