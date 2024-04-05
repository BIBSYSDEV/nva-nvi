package no.sikt.nva.nvi.events.evaluator.model;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

public record InstitutionPoints(URI institutionId,
                                BigDecimal institutionPoints,
                                List<AffiliationPoints> affiliationPoints) {

    public record AffiliationPoints(URI affiliationId, URI nviCreator, BigDecimal points) {

    }
}
