package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.isNull;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.dto.PointCalculationDto;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.InstanceType;
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
        dto.collaborationFactor(),
        dto.basePoints(),
        dto.creatorShareCount(),
        dto.institutionPoints(),
        dto.totalPoints());
  }

  public static PointCalculation from(UpsertNviCandidateRequest request) {
    return new PointCalculation(
        request.publicationDetails().publicationType(),
        PublicationChannel.from(request.publicationChannelForLevel()),
        request.isInternationalCollaboration(),
        request.collaborationFactor(),
        request.basePoints(),
        request.creatorShareCount(),
        request.institutionPoints(),
        request.totalPoints());
  }

  public static PointCalculation from(CandidateDao candidateDao) {
    var dbCandidate = candidateDao.candidate();
    return new PointCalculation(
        InstanceType.parse(dbCandidate.instanceType()),
        PublicationChannel.from(candidateDao),
        dbCandidate.internationalCollaboration(),
        dbCandidate.collaborationFactor(),
        dbCandidate.basePoints(),
        dbCandidate.creatorShareCount(),
        mapToInstitutionPoints(candidateDao),
        dbCandidate.totalPoints());
  }

  private static List<InstitutionPoints> mapToInstitutionPoints(CandidateDao candidateDao) {
    if (isNull(candidateDao.candidate().points()) || candidateDao.candidate().points().isEmpty()) {
      return Collections.emptyList();
    } else {
      return candidateDao.candidate().points().stream().map(InstitutionPoints::from).toList();
    }
  }
}
