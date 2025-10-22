package no.sikt.nva.nvi.common.service.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints.DbCreatorAffiliationPoints;

public record InstitutionPoints(
    URI institutionId,
    BigDecimal institutionPoints,
    Collection<CreatorAffiliationPoints> creatorAffiliationPoints) {

  public InstitutionPoints(
      URI institutionId,
      BigDecimal institutionPoints,
      Collection<CreatorAffiliationPoints> creatorAffiliationPoints) {
    this.institutionId = institutionId;
    this.institutionPoints = adjustScaleAndRoundingMode(institutionPoints);
    this.creatorAffiliationPoints = creatorAffiliationPoints;
  }

  public static InstitutionPoints from(DbInstitutionPoints dbInstitutionPoints) {
    var creatorAffiliationPoints =
        Optional.ofNullable(dbInstitutionPoints.creatorAffiliationPoints())
            .map(list -> list.stream().map(CreatorAffiliationPoints::from).toList())
            .orElse(emptyList());
    return new InstitutionPoints(
        dbInstitutionPoints.institutionId(),
        dbInstitutionPoints.points(),
        creatorAffiliationPoints);
  }

  @Override
  public Collection<CreatorAffiliationPoints> creatorAffiliationPoints() {
    return nonNull(creatorAffiliationPoints) ? creatorAffiliationPoints : emptyList();
  }

  public record CreatorAffiliationPoints(URI nviCreator, URI affiliationId, BigDecimal points) {

    public CreatorAffiliationPoints(URI nviCreator, URI affiliationId, BigDecimal points) {
      this.nviCreator = nviCreator;
      this.affiliationId = affiliationId;
      this.points = adjustScaleAndRoundingMode(points);
    }

    public static CreatorAffiliationPoints from(
        DbCreatorAffiliationPoints dbCreatorAffiliationPoints) {
      return new CreatorAffiliationPoints(
          dbCreatorAffiliationPoints.creatorId(),
          dbCreatorAffiliationPoints.affiliationId(),
          dbCreatorAffiliationPoints.points());
    }
  }
}
