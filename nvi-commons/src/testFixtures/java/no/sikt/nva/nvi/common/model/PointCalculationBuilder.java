package no.sikt.nva.nvi.common.model;

import java.math.BigDecimal;
import java.util.Collection;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;

public final class PointCalculationBuilder {
  private InstanceType instanceType;
  private PublicationChannel channel;
  private boolean isInternationalCollaboration;
  private BigDecimal collaborationFactor;
  private BigDecimal basePoints;
  private int creatorShareCount;
  private Collection<InstitutionPoints> institutionPoints;
  private BigDecimal totalPoints;

  public PointCalculationBuilder() {}

  public PointCalculationBuilder(PointCalculation other) {
    this.instanceType = other.instanceType();
    this.channel = other.channel();
    this.isInternationalCollaboration = other.isInternationalCollaboration();
    this.collaborationFactor = other.collaborationFactor();
    this.basePoints = other.basePoints();
    this.creatorShareCount = other.creatorShareCount();
    this.institutionPoints = other.institutionPoints();
    this.totalPoints = other.totalPoints();
  }

  public static PointCalculationBuilder builder() {
    return new PointCalculationBuilder();
  }

  public PointCalculationBuilder withInstanceType(InstanceType instanceType) {
    this.instanceType = instanceType;
    return this;
  }

  public PointCalculationBuilder withChannel(PublicationChannel channel) {
    this.channel = channel;
    return this;
  }

  public PointCalculationBuilder withIsInternationalCollaboration(
      boolean isInternationalCollaboration) {
    this.isInternationalCollaboration = isInternationalCollaboration;
    return this;
  }

  public PointCalculationBuilder withCollaborationFactor(BigDecimal collaborationFactor) {
    this.collaborationFactor = collaborationFactor;
    return this;
  }

  public PointCalculationBuilder withBasePoints(BigDecimal basePoints) {
    this.basePoints = basePoints;
    return this;
  }

  public PointCalculationBuilder withCreatorShareCount(int creatorShareCount) {
    this.creatorShareCount = creatorShareCount;
    return this;
  }

  public PointCalculationBuilder withInstitutionPoints(
      Collection<InstitutionPoints> institutionPoints) {
    this.institutionPoints = institutionPoints;
    return this;
  }

  public PointCalculationBuilder withTotalPoints(BigDecimal totalPoints) {
    this.totalPoints = totalPoints;
    return this;
  }

  public PointCalculation build() {
    return new PointCalculation(
        instanceType,
        channel,
        isInternationalCollaboration,
        collaborationFactor,
        basePoints,
        creatorShareCount,
        institutionPoints,
        totalPoints);
  }
}
