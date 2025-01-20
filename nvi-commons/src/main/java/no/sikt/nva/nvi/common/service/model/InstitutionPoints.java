package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.nonNull;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints.DbCreatorAffiliationPoints;
import no.sikt.nva.nvi.common.utils.DecimalUtils;
import nva.commons.core.JacocoGenerated;

public record InstitutionPoints(
    URI institutionId,
    BigDecimal institutionPoints,
    List<CreatorAffiliationPoints> creatorAffiliationPoints) {

  public static InstitutionPoints from(DbInstitutionPoints dbInstitutionPoints) {
    return new InstitutionPoints(
        dbInstitutionPoints.institutionId(),
        dbInstitutionPoints.points(),
        dbInstitutionPoints.creatorAffiliationPoints().stream()
            .map(CreatorAffiliationPoints::from)
            .toList());
  }

  @Override
  public List<CreatorAffiliationPoints> creatorAffiliationPoints() {
    return nonNull(creatorAffiliationPoints) ? creatorAffiliationPoints : List.of();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InstitutionPoints that = (InstitutionPoints) o;
    return Objects.equals(institutionId, that.institutionId)
        && Objects.equals(
            DecimalUtils.adjustScaleAndRoundingMode(institutionPoints),
            DecimalUtils.adjustScaleAndRoundingMode(that.institutionPoints))
        && Objects.equals(creatorAffiliationPoints, that.creatorAffiliationPoints);
  }

  @Override
  @JacocoGenerated
  public int hashCode() {
    return Objects.hash(institutionId, institutionPoints, creatorAffiliationPoints);
  }

  public record CreatorAffiliationPoints(URI nviCreator, URI affiliationId, BigDecimal points) {

    public static CreatorAffiliationPoints from(
        DbCreatorAffiliationPoints dbCreatorAffiliationPoints) {
      return new CreatorAffiliationPoints(
          dbCreatorAffiliationPoints.creatorId(),
          dbCreatorAffiliationPoints.affiliationId(),
          dbCreatorAffiliationPoints.points());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CreatorAffiliationPoints that = (CreatorAffiliationPoints) o;
      return Objects.equals(nviCreator, that.nviCreator)
          && Objects.equals(affiliationId, that.affiliationId)
          && Objects.equals(
              DecimalUtils.adjustScaleAndRoundingMode(points),
              DecimalUtils.adjustScaleAndRoundingMode(that.points));
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
      return Objects.hash(nviCreator, affiliationId, points);
    }
  }
}
