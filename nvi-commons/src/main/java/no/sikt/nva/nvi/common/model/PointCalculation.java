package no.sikt.nva.nvi.common.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbPointCalculation;
import no.sikt.nva.nvi.common.dto.PointCalculationDto;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import nva.commons.core.JacocoGenerated;

public record PointCalculation(
    InstanceType instanceType,
    PublicationChannel channel,
    boolean isInternationalCollaboration,
    BigDecimal collaborationFactor,
    BigDecimal basePoints,
    int creatorShareCount,
    List<InstitutionPoints> institutionPoints,
    BigDecimal totalPoints) {

  // FIXME: Temporary suppression while we refactor the code to use the new PointCalculationDto
  @JacocoGenerated
  public static PointCalculation from(PointCalculationDto dto) {
    return new PointCalculation(
        dto.instanceType(),
        PublicationChannel.from(dto.channel()),
        dto.isInternationalCollaboration(),
        adjustScaleAndRoundingMode(dto.collaborationFactor()),
        adjustScaleAndRoundingMode(dto.basePoints()),
        dto.creatorShareCount(),
        dto.institutionPoints(),
        adjustScaleAndRoundingMode(dto.totalPoints()));
  }

  public static PointCalculation from(UpsertNviCandidateRequest request) {
    return from(request.pointCalculation());
  }

  /**
   * @deprecated Temporary method while migrating data.
   */
  @Deprecated(since = "2025-05-05", forRemoval = true)
  public static PointCalculation from(CandidateDao candidateDao) {
    var dbCandidate = candidateDao.candidate();
    var dbPointCalculation = dbCandidate.pointCalculation();
    if (nonNull(dbPointCalculation)) {
      return from(dbPointCalculation);
    }
    return new PointCalculation(
        InstanceType.parse(dbCandidate.instanceType()),
        PublicationChannel.from(candidateDao),
        dbCandidate.internationalCollaboration(),
        dbCandidate.collaborationFactor(),
        dbCandidate.basePoints(),
        dbCandidate.creatorShareCount(),
        mapToInstitutionPoints(dbCandidate.points()),
        dbCandidate.totalPoints());
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
    if (isNull(dbPoints) || dbPoints.isEmpty()) {
      return Collections.emptyList();
    } else {
      return dbPoints.stream().map(InstitutionPoints::from).toList();
    }
  }

  private static List<DbInstitutionPoints> mapToDbInstitutionPoints(
      List<InstitutionPoints> points) {
    return points.stream().map(DbInstitutionPoints::from).toList();
  }
}
