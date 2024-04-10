package no.sikt.nva.nvi.events.evaluator.model;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

public record InstitutionPoints(URI institutionId,
                                BigDecimal institutionPoints,
                                List<CreatorAffiliationPoints> creatorAffiliationPoints) {

    public no.sikt.nva.nvi.common.service.model.InstitutionPoints toInstitutionPoints() {
        return new no.sikt.nva.nvi.common.service.model.InstitutionPoints(institutionId, institutionPoints,
                                                                          getCreatorAffiliationPoints());
    }

    private List<no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints>
    getCreatorAffiliationPoints() {
        return creatorAffiliationPoints.stream()
                   .map(CreatorAffiliationPoints::toCreatorAffiliationPoints)
                   .toList();
    }

    public record CreatorAffiliationPoints(URI affiliationId, URI nviCreator, BigDecimal points) {

        public no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints
        toCreatorAffiliationPoints() {
            return new no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints(
                nviCreator, affiliationId, points);
        }
    }
}
