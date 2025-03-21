package no.sikt.nva.nvi.common;

import static java.math.BigDecimal.ZERO;
import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;

@SuppressWarnings("PMD.TooManyFields")
public class UpsertRequestBuilder {

  private URI publicationBucketUri;
  private URI publicationId;
  private boolean isApplicable;
  private boolean isInternationalCollaboration;
  private Map<URI, List<URI>> creators;
  private Collection<UnverifiedNviCreatorDto> unverifiedCreators;
  private Collection<VerifiedNviCreatorDto> verifiedCreators;
  private String channelType;
  private URI channelId;
  private String level;
  private InstanceType instanceType;
  private PublicationDate publicationDate;
  private int creatorShareCount;
  private BigDecimal collaborationFactor;
  private BigDecimal basePoints;
  private List<InstitutionPoints> points;
  private BigDecimal totalPoints;

  public static UpsertRequestBuilder randomUpsertRequestBuilder() {
    final URI creatorId = randomUri();
    final URI affiliationId = randomUri();
    return new UpsertRequestBuilder()
        .withPublicationBucketUri(randomUri())
        .withPublicationId(randomUri())
        .withIsApplicable(true)
        .withIsInternationalCollaboration(true)
        .withCreators(Map.of(creatorId, List.of(affiliationId)))
        .withVerifiedCreators(List.of(new VerifiedNviCreatorDto(creatorId, List.of(affiliationId))))
        .withUnverifiedCreators(emptyList())
        .withChannelType(ChannelType.JOURNAL.getValue())
        .withChannelId(randomUri())
        .withLevel("LevelOne")
        .withInstanceType(InstanceType.ACADEMIC_ARTICLE)
        .withPublicationDate(new PublicationDate(String.valueOf(CURRENT_YEAR), "01", "01"))
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

  public static UpsertRequestBuilder fromRequest(UpsertCandidateRequest request) {
    return new UpsertRequestBuilder()
        .withPublicationBucketUri(request.publicationBucketUri())
        .withPublicationId(request.publicationId())
        .withIsApplicable(request.isApplicable())
        .withIsInternationalCollaboration(request.isInternationalCollaboration())
        .withCreators(request.creators())
        .withVerifiedCreators(request.verifiedCreators())
        .withUnverifiedCreators(request.unverifiedCreators())
        .withChannelType(request.channelType())
        .withChannelId(request.publicationChannelId())
        .withLevel(request.level())
        .withInstanceType(request.instanceType())
        .withPublicationDate(request.publicationDate())
        .withCreatorShareCount(request.creatorShareCount())
        .withCollaborationFactor(request.collaborationFactor())
        .withBasePoints(request.basePoints())
        .withPoints(request.institutionPoints())
        .withTotalPoints(request.totalPoints());
  }

  public UpsertRequestBuilder withPublicationBucketUri(URI publicationBucketUri) {
    this.publicationBucketUri = publicationBucketUri;
    return this;
  }

  public UpsertRequestBuilder withPublicationId(URI publicationId) {
    this.publicationId = publicationId;
    return this;
  }

  public UpsertRequestBuilder withIsApplicable(boolean isApplicable) {
    this.isApplicable = isApplicable;
    return this;
  }

  public UpsertRequestBuilder withIsInternationalCollaboration(
      boolean isInternationalCollaboration) {
    this.isInternationalCollaboration = isInternationalCollaboration;
    return this;
  }

  public UpsertRequestBuilder withCreators(Map<URI, List<URI>> creators) {
    this.creators = creators;
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
    return this;
  }

  public UpsertRequestBuilder withPublicationDate(PublicationDate publicationDate) {
    this.publicationDate = publicationDate;
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

  public UpsertCandidateRequest build() {

    return new UpsertCandidateRequest() {

      @Override
      public URI publicationBucketUri() {
        return publicationBucketUri;
      }

      @Override
      public URI publicationId() {
        return publicationId;
      }

      @Override
      public boolean isApplicable() {
        return isApplicable;
      }

      @Override
      public boolean isInternationalCollaboration() {
        return isInternationalCollaboration;
      }

      @Override
      public Map<URI, List<URI>> creators() {
        return creators;
      }

      @Override
      public List<VerifiedNviCreatorDto> verifiedCreators() {
        return List.copyOf(verifiedCreators);
      }

      @Override
      public List<UnverifiedNviCreatorDto> unverifiedCreators() {
        return List.copyOf(unverifiedCreators);
      }

      @Override
      public String channelType() {
        return channelType;
      }

      @Override
      public URI publicationChannelId() {
        return channelId;
      }

      @Override
      public String level() {
        return level;
      }

      @Override
      public InstanceType instanceType() {
        return instanceType;
      }

      @Override
      public PublicationDate publicationDate() {
        return publicationDate;
      }

      @Override
      public int creatorShareCount() {
        return creatorShareCount;
      }

      @Override
      public BigDecimal collaborationFactor() {
        return collaborationFactor;
      }

      @Override
      public BigDecimal basePoints() {
        return basePoints;
      }

      @Override
      public List<InstitutionPoints> institutionPoints() {
        return points;
      }

      @Override
      public BigDecimal totalPoints() {
        return totalPoints;
      }
    };
  }
}
