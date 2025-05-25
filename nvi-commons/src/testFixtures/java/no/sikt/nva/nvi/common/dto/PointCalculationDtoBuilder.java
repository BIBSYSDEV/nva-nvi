package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.common.examples.ExamplePublicationChannels.JOURNAL_OF_TESTING;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;

public class PointCalculationDtoBuilder {

  private InstanceType instanceType;
  private PublicationChannelDto channel;
  private boolean isInternationalCollaboration;
  private BigDecimal collaborationFactor;
  private BigDecimal basePoints;
  private int creatorShareCount;
  private List<InstitutionPoints> institutionPoints = new ArrayList<>();
  private BigDecimal totalPoints;

  public PointCalculationDtoBuilder() {}

  public PointCalculationDtoBuilder(PointCalculationDto other) {
    this.instanceType = other.instanceType();
    this.channel = other.channel();
    this.isInternationalCollaboration = other.isInternationalCollaboration();
    this.collaborationFactor = other.collaborationFactor();
    this.basePoints = other.basePoints();
    this.creatorShareCount = other.creatorShareCount();
    this.institutionPoints = other.institutionPoints();
    this.totalPoints = other.totalPoints();
  }

  public static PointCalculationDtoBuilder randomPointCalculationDtoBuilder() {
    var creatorPoint = new CreatorAffiliationPoints(randomUri(), randomUri(), randomBigDecimal());
    var institutionPoint =
        new InstitutionPoints(randomUri(), randomBigDecimal(), List.of(creatorPoint));
    return builder()
        .withInstanceType(InstanceType.ACADEMIC_ARTICLE)
        .withChannel(JOURNAL_OF_TESTING)
        .withIsInternationalCollaboration(true)
        .withCollaborationFactor(BigDecimal.ONE)
        .withBasePoints(BigDecimal.ONE)
        .withCreatorShareCount(randomInteger())
        .withInstitutionPoints(List.of(institutionPoint))
        .withTotalPoints(BigDecimal.ONE);
  }

  public static PointCalculationDto randomPointCalculationDto() {
    return randomPointCalculationDtoBuilder().build();
  }

  public static PointCalculationDtoBuilder builder() {
    return new PointCalculationDtoBuilder();
  }

  public PointCalculationDtoBuilder withInstanceType(InstanceType instanceType) {
    this.instanceType = instanceType;
    return this;
  }

  public PointCalculationDtoBuilder withChannel(PublicationChannelDto channel) {
    this.channel = channel;
    return this;
  }

  public PointCalculationDtoBuilder withIsInternationalCollaboration(
      boolean isInternationalCollaboration) {
    this.isInternationalCollaboration = isInternationalCollaboration;
    return this;
  }

  public PointCalculationDtoBuilder withCollaborationFactor(BigDecimal collaborationFactor) {
    this.collaborationFactor = collaborationFactor;
    return this;
  }

  public PointCalculationDtoBuilder withBasePoints(BigDecimal basePoints) {
    this.basePoints = basePoints;
    return this;
  }

  public PointCalculationDtoBuilder withCreatorShareCount(int creatorShareCount) {
    this.creatorShareCount = creatorShareCount;
    return this;
  }

  public PointCalculationDtoBuilder withInstitutionPoints(
      List<InstitutionPoints> institutionPoints) {
    this.institutionPoints = institutionPoints;
    return this;
  }

  public PointCalculationDtoBuilder withInstitutionPointFor(
      URI topLeveOrganizationId, URI affiliationId, URI... creatorIds) {
    var pointValue = randomBigDecimal();
    var creatorPoints =
        List.of(creatorIds).stream()
            .map(creatorId -> new CreatorAffiliationPoints(creatorId, affiliationId, pointValue))
            .toList();
    var institutionPoint = new InstitutionPoints(topLeveOrganizationId, pointValue, creatorPoints);
    var updatedList = new ArrayList<>(institutionPoints);
    updatedList.add(institutionPoint);
    return this.withInstitutionPoints(updatedList);
  }

  public PointCalculationDtoBuilder withTotalPoints(BigDecimal totalPoints) {
    this.totalPoints = totalPoints;
    return this;
  }

  public PointCalculationDto build() {
    return new PointCalculationDto(
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
