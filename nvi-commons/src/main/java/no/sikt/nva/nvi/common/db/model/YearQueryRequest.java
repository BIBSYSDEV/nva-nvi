package no.sikt.nva.nvi.common.db.model;

import java.util.Map;
import no.sikt.nva.nvi.common.db.request.CandidateScanParameters;

public record YearQueryRequest(String year, int batchSize, Map<String, String> lastItemRead)
    implements CandidateScanParameters {}
