package no.sikt.nva.nvi.common.db.model;

import java.util.Map;
import no.sikt.nva.nvi.common.db.request.CandidateScanRequest;

public record YearQueryRequest(String year, int batchSize, Map<String, String> lastItemRead)
    implements CandidateScanRequest {}
