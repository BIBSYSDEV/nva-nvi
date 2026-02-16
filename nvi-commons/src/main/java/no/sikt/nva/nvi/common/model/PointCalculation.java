package no.sikt.nva.nvi.common.model;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.copyOfNullable;
import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;
import static no.sikt.nva.nvi.common.utils.Validator.hasElements;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbPointCalculation;
import no.sikt.nva.nvi.common.dto.PointCalculationDto;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;

public record PointCalculation(
    InstanceType instanceType,
    PublicationChannel channel,
    boolean isInternationalCollaboration,
    BigDecimal collaborationFactor,
    BigDecimal basePoints,
    int creatorShareCount,
    Collection<InstitutionPoints> institutionPoints,
    BigDecimal totalPoints) {

  public PointCalculation {
    institutionPoints = copyOfNullable(institutionPoints);
    collaborationFactor = adjustScaleAndRoundingMode(collaborationFactor);
    basePoints = adjustScaleAndRoundingMode(basePoints);
    totalPoints = adjustScaleAndRoundingMode(totalPoints);
  }

  public static PointCalculation from(PointCalculationDto dto) {
    return new PointCalculation(
        dto.instanceType(),
        PublicationChannel.from(dto.channel()),
        dto.isInternationalCollaboration(),
        dto.collaborationFactor(),
        dto.basePoints(),
        dto.creatorShareCount(),
        dto.institutionPoints(),
        dto.totalPoints());
  }

  public static PointCalculation from(UpsertNviCandidateRequest request) {
    return from(request.pointCalculation());
  }

  public static PointCalculation from(DbPointCalculation dbPointCalculation) {
    return new PointCalculation(
        InstanceType.parse(dbPointCalculation.instanceType()),
        PublicationChannel.from(dbPointCalculation.publicationChannel()),
        dbPointCalculation.internationalCollaboration(),
        dbPointCalculation.collaborationFactor(),
        dbPointCalculation.basePoints(),
        dbPointCalculation.creatorShareCount(),
        mapToInstitutionPoints(dbPointCalculation.institutionPoints()),
        dbPointCalculation.totalPoints());
  }

  public DbPointCalculation toDbPointCalculation() {
    return new DbPointCalculation(
        basePoints,
        collaborationFactor,
        totalPoints,
        channel.toDbPublicationChannel(),
        mapToDbInstitutionPoints(institutionPoints),
        isInternationalCollaboration,
        creatorShareCount,
        instanceType.getValue());
  }

  private static List<InstitutionPoints> mapToInstitutionPoints(
      Collection<DbInstitutionPoints> dbPoints) {
    if (hasElements(dbPoints)) {
      return dbPoints.stream().map(InstitutionPoints::from).toList();
    }
    return emptyList();
  }

  private static List<DbInstitutionPoints> mapToDbInstitutionPoints(
      Collection<InstitutionPoints> points) {
    return points.stream().map(DbInstitutionPoints::from).toList();
  }
}
