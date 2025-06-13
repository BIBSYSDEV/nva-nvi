package no.sikt.nva.nvi.common;

import static java.math.BigDecimal.ZERO;
import static no.sikt.nva.nvi.common.dto.PointCalculationDtoBuilder.randomPointCalculationDto;
import static no.sikt.nva.nvi.common.dto.PublicationDetailsDtoBuilder.randomPublicationDetailsDto;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.randomVerifiedNviCreatorDto;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomTopLevelOrganization;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PointCalculationDto;
import no.sikt.nva.nvi.common.dto.PointCalculationDtoBuilder;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.dto.PublicationDetailsDto;
import no.sikt.nva.nvi.common.dto.PublicationDetailsDtoBuilder;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;

public class UpsertRequestBuilder {

  private URI publicationBucketUri;
  private PublicationDetailsDto publicationDetails;
  private PointCalculationDto pointCalculation;
  private List<NviCreatorDto> nviCreators;
  private List<Organization> topLevelNviOrganizations;

  public static UpsertRequestBuilder randomUpsertRequestBuilder() {
    var topLevelOrganization = randomTopLevelOrganization();
    var affiliationId = topLevelOrganization.hasPart().getFirst().id();
    var nviCreator = randomVerifiedNviCreatorDto(affiliationId);

    return new UpsertRequestBuilder()
        .withPublicationBucketUri(randomUri())
        .withPointCalculation(randomPointCalculationDto())
        .withCreatorsAndPoints(Map.of(topLevelOrganization, List.of(nviCreator)))
        .withPublicationDetails(randomPublicationDetailsDto());
  }

  public static UpsertRequestBuilder fromRequest(UpsertNviCandidateRequest request) {
    var pointCalculation = request.pointCalculation();
    return new UpsertRequestBuilder()
        .withPublicationBucketUri(request.publicationBucketUri())
        .withPointCalculation(pointCalculation)
        .withPublicationDetails(request.publicationDetails())
        .withNviCreators(request.nviCreators())
        .withTopLevelOrganizations(request.topLevelNviOrganizations());
  }

  public UpsertRequestBuilder withPublicationBucketUri(URI publicationBucketUri) {
    this.publicationBucketUri = publicationBucketUri;
    return this;
  }

  public UpsertRequestBuilder withPublicationDetails(PublicationDetailsDto publicationDetails) {
    this.publicationDetails = publicationDetails;
    return this;
  }

  public UpsertRequestBuilder withPointCalculation(PointCalculationDto pointCalculation) {
    this.pointCalculation = pointCalculation;
    return this;
  }

  public UpsertRequestBuilder withPublicationId(URI publicationId) {
    this.publicationDetails =
        new PublicationDetailsDtoBuilder(publicationDetails).withId(publicationId).build();
    return this;
  }

  public UpsertRequestBuilder withNviCreators(NviCreatorDto... nviCreators) {
    this.nviCreators = List.of(nviCreators);
    return this;
  }

  public UpsertRequestBuilder withNviCreators(Collection<NviCreatorDto> nviCreators) {
    this.nviCreators = List.copyOf(nviCreators);
    return this;
  }

  public UpsertRequestBuilder withTopLevelOrganizations(Organization... topLevelOrganizations) {
    return withTopLevelOrganizations(List.of(topLevelOrganizations));
  }

  public UpsertRequestBuilder withTopLevelOrganizations(
      Collection<Organization> topLevelOrganizations) {
    this.topLevelNviOrganizations = List.copyOf(topLevelOrganizations);
    return this;
  }

  public UpsertRequestBuilder withPublicationChannel(PublicationChannelDto channel) {
    this.pointCalculation =
        new PointCalculationDtoBuilder(pointCalculation).withChannel(channel).build();
    return this;
  }

  public UpsertRequestBuilder withInstanceType(InstanceType instanceType) {
    this.pointCalculation =
        new PointCalculationDtoBuilder(pointCalculation).withInstanceType(instanceType).build();
    return this;
  }

  public UpsertRequestBuilder withPublicationDate(PublicationDateDto publicationDate) {
    this.publicationDetails =
        new PublicationDetailsDtoBuilder(publicationDetails)
            .withPublicationDate(publicationDate)
            .build();
    return this;
  }

  public UpsertRequestBuilder withPoints(List<InstitutionPoints> points) {
    this.pointCalculation =
        new PointCalculationDtoBuilder(pointCalculation).withInstitutionPoints(points).build();
    return this;
  }

  // Sets all creator and point fields based on the creatorsPerInstitution map
  public UpsertRequestBuilder withCreatorsAndPoints(
      Map<Organization, Collection<NviCreatorDto>> creatorsPerInstitution) {
    var creators = getNviCreators(creatorsPerInstitution);
    var points = getAllInstitutionPoints(creatorsPerInstitution);
    var totalPoints =
        points.stream().map(InstitutionPoints::institutionPoints).reduce(ZERO, BigDecimal::add);
    var topLevelOrganizations = creatorsPerInstitution.keySet().stream().toList();
    var updatedPointCalculation =
        new PointCalculationDtoBuilder(pointCalculation)
            .withInstitutionPoints(points)
            .withTotalPoints(totalPoints)
            .build();
    return this.withNviCreators(creators)
        .withTopLevelOrganizations(topLevelOrganizations)
        .withPointCalculation(updatedPointCalculation);
  }

  private static List<NviCreatorDto> getNviCreators(
      Map<Organization, Collection<NviCreatorDto>> creatorsPerInstitution) {
    return creatorsPerInstitution.values().stream().flatMap(Collection::stream).toList();
  }

  private List<InstitutionPoints> getAllInstitutionPoints(
      Map<Organization, Collection<NviCreatorDto>> creatorsPerInstitution) {
    return creatorsPerInstitution.entrySet().stream().map(this::getInstitutionPoints).toList();
  }

  private InstitutionPoints getInstitutionPoints(
      Map.Entry<Organization, Collection<NviCreatorDto>> entry) {
    var institution = entry.getKey();
    var creators = entry.getValue();
    var creatorPoints = getAllCreatorPoints(creators);
    var institutionTotal =
        creatorPoints.stream().map(CreatorAffiliationPoints::points).reduce(ZERO, BigDecimal::add);
    return new InstitutionPoints(institution.id(), institutionTotal, creatorPoints);
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
        .map(
            affiliation ->
                new CreatorAffiliationPoints(
                    creator.id(), affiliation, pointCalculation.basePoints()))
        .toList();
  }

  public UpsertNviCandidateRequest build() {
    return UpsertNviCandidateRequest.builder()
        .withPointCalculation(pointCalculation)
        .withPublicationDetails(publicationDetails)
        .withPublicationBucketUri(publicationBucketUri)
        .withNviCreators(nviCreators)
        .withTopLevelNviOrganizations(topLevelNviOrganizations)
        .build();
  }
}
