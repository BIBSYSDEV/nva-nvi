package no.sikt.nva.nvi.common.model;

import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.db.CandidateDao;

public class ListingResultWithCandidates extends ListingResult {

    private final List<CandidateDao> candidates;

    public ListingResultWithCandidates(boolean shouldContinueScan, Map<String, String> startMarker, int totalItem,
                                       List<CandidateDao> candidates) {
        super(shouldContinueScan, startMarker, totalItem);
        this.candidates = candidates;
    }

    public List<CandidateDao> getCandidates() {
        return candidates;
    }
}
