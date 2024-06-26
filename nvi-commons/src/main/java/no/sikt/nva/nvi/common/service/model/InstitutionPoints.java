package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.nonNull;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints.DbCreatorAffiliationPoints;

public record InstitutionPoints(URI institutionId, BigDecimal institutionPoints,
                                List<CreatorAffiliationPoints> creatorAffiliationPoints) {

    public static InstitutionPoints from(DbInstitutionPoints dbInstitutionPoints) {
        return new InstitutionPoints(dbInstitutionPoints.institutionId(), dbInstitutionPoints.points(),
                                     dbInstitutionPoints.creatorAffiliationPoints()
                                         .stream()
                                         .map(CreatorAffiliationPoints::from)
                                         .toList());
    }

    @Override
    public List<CreatorAffiliationPoints> creatorAffiliationPoints() {
        return nonNull(creatorAffiliationPoints) ? creatorAffiliationPoints : List.of();
    }

    public record CreatorAffiliationPoints(URI nviCreator, URI affiliationId, BigDecimal points) {

        public static CreatorAffiliationPoints from(DbCreatorAffiliationPoints dbCreatorAffiliationPoints) {
            return new CreatorAffiliationPoints(dbCreatorAffiliationPoints.creatorId(),
                                                dbCreatorAffiliationPoints.affiliationId(),
                                                dbCreatorAffiliationPoints.points());
        }
    }
}
