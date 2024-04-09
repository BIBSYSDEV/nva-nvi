package no.sikt.nva.nvi.common.service.model;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

public record InstitutionPoints(URI institutionId,
                                BigDecimal institutionPoints,
                                List<CreatorAffiliationPoints> creatorAffiliationPoints) {

    public record CreatorAffiliationPoints(URI nviCreator, URI affiliationId, BigDecimal points) {

    }
}
