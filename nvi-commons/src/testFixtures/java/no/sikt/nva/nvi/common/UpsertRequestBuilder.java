package no.sikt.nva.nvi.common;

import static java.math.BigDecimal.ZERO;
import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.randomVerifiedNviCreatorDto;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomTopLevelOrganization;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.dto.PublicationDtoBuilder;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;

public class UpsertRequestBuilder {

  private PublicationDtoBuilder publicationBuilder = new PublicationDtoBuilder();
  private URI publicationBucketUri;
  private URI publicationId;
  private String publicationIdentifier;
  private boolean isInternationalCollaboration;
  private Collection<UnverifiedNviCreatorDto> unverifiedCreators;
  private Collection<VerifiedNviCreatorDto> verifiedCreators;
  private String channelType;
  private URI channelId;
  private String level;
  private InstanceType instanceType;
  private int creatorShareCount;
  private BigDecimal collaborationFactor;
  private BigDecimal basePoints;
  private List<InstitutionPoints> points;
  private BigDecimal totalPoints;

  public static UpsertRequestBuilder randomUpsertRequestBuilder() {
    var topLevelOrganization = randomTopLevelOrganization();
    var affiliationId = topLevelOrganization.hasPart().getFirst().id();
    var nviCreator = randomVerifiedNviCreatorDto(affiliationId);
    var publicationBuilder =
        PublicationDtoBuilder.randomPublicationDtoBuilder()
            .withTopLevelOrganizations(List.of(topLevelOrganization));
    var publicationDetails = publicationBuilder.build();
    var channel = List.copyOf(publicationDetails.publicationChannels()).getFirst();

    return new UpsertRequestBuilder()
        .withPublicationDetails(publicationBuilder)
        .withPublicationBucketUri(randomUri())
        .withPublicationId(publicationDetails.id())
        .withPublicationIdentifier(publicationDetails.identifier())
        .withIsInternationalCollaboration(publicationDetails.isInternationalCollaboration())
        .withVerifiedCreators(List.of(nviCreator))
        .withUnverifiedCreators(emptyList())
        .withChannelType(channel.channelType().getValue())
        .withChannelId(channel.id())
        .withLevel(channel.scientificValue().getValue())
        .withInstanceType(publicationDetails.publicationType())
        .withPublicationDate(publicationDetails.publicationDate())
        .withCreatorShareCount(1)
        .withCollaborationFactor(BigDecimal.ONE)
        .withBasePoints(BigDecimal.ONE)
        .withPoints(
            List.of(
                new InstitutionPoints(
                    randomUri(),
                    randomBigDecimal(),
                    List.of(
                        new CreatorAffiliationPoints(
                            nviCreator.id(), affiliationId, randomBigDecimal())))))
        .withTotalPoints(BigDecimal.ONE);
  }

  public static UpsertRequestBuilder randomUpsertRequestBuilder(
      PublicationDtoBuilder publicationBuilder) {
    var publicationDetails = publicationBuilder.build();
    var creatorId = randomUri();
    var affiliationId = randomUri();
    var channel = List.copyOf(publicationDetails.publicationChannels()).getFirst();
    return new UpsertRequestBuilder()
        .withPublicationDetails(publicationBuilder)
        .withPublicationBucketUri(randomUri())
        .withPublicationId(publicationDetails.id())
        .withPublicationIdentifier(publicationDetails.identifier())
        .withIsInternationalCollaboration(publicationDetails.isInternationalCollaboration())
        .withVerifiedCreators(List.of(new VerifiedNviCreatorDto(creatorId, List.of(affiliationId))))
        .withUnverifiedCreators(emptyList())
        .withChannelType(channel.channelType().getValue())
        .withChannelId(channel.id())
        .withLevel(channel.scientificValue().getValue())
        .withInstanceType(publicationDetails.publicationType())
        .withPublicationDate(publicationDetails.publicationDate())
        .withCreatorShareCount(1)
        .withCollaborationFactor(BigDecimal.ONE)
        .withBasePoints(BigDecimal.ONE)
        .withPoints(
            List.of(
                new InstitutionPoints(
                    randomUri(),
                    randomBigDecimal(),
                    List.of(
                        new CreatorAffiliationPoints(
                            creatorId, affiliationId, randomBigDecimal())))))
        .withTotalPoints(BigDecimal.ONE);
  }

  // TODO: Update this
  public static UpsertRequestBuilder fromRequest(UpsertNviCandidateRequest request) {
    var publicationBuilder = PublicationDtoBuilder.fromRequest(request);
    var publicationDetails = publicationBuilder.build();
    return new UpsertRequestBuilder()
        .withPublicationBucketUri(request.publicationBucketUri())
        .withPublicationId(publicationDetails.id())
        .withPublicationDetails(publicationBuilder)
        .withIsInternationalCollaboration(publicationDetails.isInternationalCollaboration())
        .withVerifiedCreators(request.verifiedCreators())
        .withUnverifiedCreators(request.unverifiedCreators())
        .withChannelType(request.channelType())
        .withChannelId(request.publicationChannelId())
        .withLevel(request.level())
        .withInstanceType(publicationDetails.publicationType())
        .withPublicationDate(publicationDetails.publicationDate())
        .withCreatorShareCount(request.creatorShareCount())
        .withCollaborationFactor(request.collaborationFactor())
        .withBasePoints(request.basePoints())
        .withPoints(request.institutionPoints())
        .withTotalPoints(request.totalPoints());
  }

  public UpsertRequestBuilder withPublicationDetails(PublicationDtoBuilder publicationBuilder) {
    this.publicationBuilder = publicationBuilder;
    return this;
  }

  public UpsertRequestBuilder withPublicationBucketUri(URI publicationBucketUri) {
    this.publicationBucketUri = publicationBucketUri;
    return this;
  }

  public UpsertRequestBuilder withPublicationId(URI publicationId) {
    this.publicationId = publicationId;
    this.publicationBuilder = publicationBuilder.withId(publicationId);
    return this;
  }

  public UpsertRequestBuilder withPublicationIdentifier(String publicationIdentifier) {
    this.publicationIdentifier = publicationIdentifier;
    this.publicationBuilder = publicationBuilder.withIdentifier(publicationIdentifier);
    return this;
  }

  public UpsertRequestBuilder withIsInternationalCollaboration(
      boolean isInternationalCollaboration) {
    this.isInternationalCollaboration = isInternationalCollaboration;
    this.publicationBuilder =
        publicationBuilder.withIsInternationalCollaboration(isInternationalCollaboration);
    return this;
  }

  public UpsertRequestBuilder withVerifiedCreators(
      Collection<VerifiedNviCreatorDto> verifiedCreators) {
    this.verifiedCreators = verifiedCreators;
    return this;
  }

  public UpsertRequestBuilder withUnverifiedCreators(
      Collection<UnverifiedNviCreatorDto> unverifiedCreators) {
    this.unverifiedCreators = unverifiedCreators;
    return this;
  }

  public UpsertRequestBuilder withChannelType(String channelType) {
    this.channelType = channelType;
    return this;
  }

  public UpsertRequestBuilder withChannelId(URI channelId) {
    this.channelId = channelId;
    return this;
  }

  public UpsertRequestBuilder withLevel(String level) {
    this.level = level;
    return this;
  }

  public UpsertRequestBuilder withInstanceType(InstanceType instanceType) {
    this.instanceType = instanceType;
    this.publicationBuilder = publicationBuilder.withPublicationType(instanceType);
    return this;
  }

  public UpsertRequestBuilder withPublicationDate(PublicationDateDto publicationDate) {
    this.publicationBuilder = publicationBuilder.withPublicationDate(publicationDate);
    return this;
  }

  public UpsertRequestBuilder withCreatorShareCount(int creatorShareCount) {
    this.creatorShareCount = creatorShareCount;
    return this;
  }

  public UpsertRequestBuilder withCollaborationFactor(BigDecimal collaborationFactor) {
    this.collaborationFactor = collaborationFactor;
    return this;
  }

  public UpsertRequestBuilder withBasePoints(BigDecimal basePoints) {
    this.basePoints = basePoints;
    return this;
  }

  public UpsertRequestBuilder withPoints(List<InstitutionPoints> points) {
    this.points = points;
    return this;
  }

  public UpsertRequestBuilder withTotalPoints(BigDecimal totalPoints) {
    this.totalPoints = totalPoints;
    return this;
  }

  // Sets all creator and point fields based on the creatorsPerInstitution map
  public UpsertRequestBuilder withCreatorsAndPoints(
      Map<URI, Collection<NviCreatorDto>> creatorsPerInstitution) {
    this.verifiedCreators = getVerifiedCreators(creatorsPerInstitution);
    this.unverifiedCreators = getUnverifiedCreators(creatorsPerInstitution);
    this.points = getAllInstitutionPoints(creatorsPerInstitution);
    this.totalPoints =
        points.stream().map(InstitutionPoints::institutionPoints).reduce(ZERO, BigDecimal::add);
    return this;
  }

  private static List<VerifiedNviCreatorDto> getVerifiedCreators(
      Map<URI, Collection<NviCreatorDto>> creatorsPerInstitution) {
    return creatorsPerInstitution.values().stream()
        .flatMap(Collection::stream)
        .filter(VerifiedNviCreatorDto.class::isInstance)
        .map(VerifiedNviCreatorDto.class::cast)
        .toList();
  }

  private static List<UnverifiedNviCreatorDto> getUnverifiedCreators(
      Map<URI, Collection<NviCreatorDto>> creatorsPerInstitution) {
    return creatorsPerInstitution.values().stream()
        .flatMap(Collection::stream)
        .filter(UnverifiedNviCreatorDto.class::isInstance)
        .map(UnverifiedNviCreatorDto.class::cast)
        .toList();
  }

  private List<InstitutionPoints> getAllInstitutionPoints(
      Map<URI, Collection<NviCreatorDto>> creatorsPerInstitution) {
    return creatorsPerInstitution.entrySet().stream().map(this::getInstitutionPoints).toList();
  }

  private InstitutionPoints getInstitutionPoints(Map.Entry<URI, Collection<NviCreatorDto>> entry) {
    var institution = entry.getKey();
    var creators = entry.getValue();
    var creatorPoints = getAllCreatorPoints(creators);
    var institutionTotal =
        creatorPoints.stream().map(CreatorAffiliationPoints::points).reduce(ZERO, BigDecimal::add);
    return new InstitutionPoints(institution, institutionTotal, creatorPoints);
  }

  private List<CreatorAffiliationPoints> getAllCreatorPoints(Collection<NviCreatorDto> creators) {
    return creators.stream()
        .filter(VerifiedNviCreatorDto.class::isInstance)
        .map(VerifiedNviCreatorDto.class::cast)
        .map(this::getCreatorPoints)
        .flatMap(Collection::stream)
        .toList();
  }

  private List<CreatorAffiliationPoints> getCreatorPoints(VerifiedNviCreatorDto creator) {
    return creator.affiliations().stream()
        .map(affiliation -> new CreatorAffiliationPoints(creator.id(), affiliation, basePoints))
        .toList();
  }

  public UpsertNviCandidateRequest build() {
    return UpsertNviCandidateRequest.builder()
        .withPublicationDetails(publicationBuilder.build())
        .withPublicationId(publicationId)
        .withPublicationBucketUri(publicationBucketUri)
        .withIsInternationalCollaboration(isInternationalCollaboration)
        .withCollaborationFactor(collaborationFactor)
        .withVerifiedNviCreators(List.copyOf(verifiedCreators))
        .withUnverifiedNviCreators(List.copyOf(unverifiedCreators))
        .withChannelType(channelType)
        .withPublicationChannelId(channelId)
        .withLevel(level)
        .withInstanceType(instanceType)
        .withCreatorShareCount(creatorShareCount)
        .withBasePoints(basePoints)
        .withInstitutionPoints(List.copyOf(points))
        .withTotalPoints(totalPoints)
        .build();
  }
}
