package no.sikt.nva.nvi.events.evaluator.model;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

public record InstitutionPoints(URI institutionId, List<AffiliationPoints> affiliationPoints) {

    public BigDecimal getPoints() {
        return affiliationPoints.stream()
                   .map(AffiliationPoints::points)
                   .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record AffiliationPoints(URI affiliationId, URI nviCreator, BigDecimal points) {

    }
}
