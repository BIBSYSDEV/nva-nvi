package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;

import java.math.BigDecimal;
import java.util.List;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;

public record PointCalculationDto(
    InstanceType instanceType,
    PublicationChannelDto channel,
    boolean isInternationalCollaboration,
    BigDecimal collaborationFactor,
    BigDecimal basePoints,
    int creatorShareCount,
    List<InstitutionPoints> institutionPoints,
    BigDecimal totalPoints) {

  public void validate() {
    shouldNotBeNull(instanceType, "Required field 'instanceType' is null");
    shouldNotBeNull(channel, "Required field 'channel' is null");
    shouldNotBeNull(
        isInternationalCollaboration, "Required field 'isInternationalCollaboration' is null");
    shouldNotBeNull(collaborationFactor, "Required field 'collaborationFactor' is null");
    shouldNotBeNull(basePoints, "Required field 'basePoints' is null");
    shouldNotBeNull(creatorShareCount, "Required field 'creatorShareCount' is null");
    shouldNotBeNull(institutionPoints, "Required field 'institutionPoints' is null");
    shouldNotBeNull(totalPoints, "Required field 'totalPoints' is null");
  }
}
