package no.sikt.nva.nvi.common.dto;

import java.math.BigDecimal;
import java.util.List;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;

// TODO: Make DTO and DB versions of this and persist it as a separate field
public record PointCalculationDto(
    InstanceType instanceType,
    PublicationChannelDto channel,
    boolean isInternationalCollaboration,
    BigDecimal collaborationFactor,
    BigDecimal basePoints,
    int creatorShareCount,
    List<InstitutionPoints> institutionPoints,
    BigDecimal totalPoints) {}
