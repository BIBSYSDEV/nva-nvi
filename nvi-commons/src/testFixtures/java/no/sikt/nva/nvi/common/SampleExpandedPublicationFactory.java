package no.sikt.nva.nvi.common;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.createCustomer;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganization;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateInCurrentYear;
import static no.sikt.nva.nvi.common.utils.Validator.hasElements;
import static no.sikt.nva.nvi.test.TestConstants.ACADEMIC_CHAPTER;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static no.sikt.nva.nvi.test.TestConstants.JOURNAL_TYPE;
import static no.sikt.nva.nvi.test.TestConstants.LEVEL_ONE;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.testutils.RandomDataGenerator.randomIsbn13;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.sikt.nva.nvi.test.SampleExpandedAffiliation;
import no.sikt.nva.nvi.test.SampleExpandedContributor;
import no.sikt.nva.nvi.test.SampleExpandedOrganization;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.sikt.nva.nvi.test.SampleExpandedPublicationChannel;
import no.sikt.nva.nvi.test.SampleExpandedPublicationContext;
import no.sikt.nva.nvi.test.SampleExpandedPublicationDate;
import no.unit.nva.clients.CustomerDto;

// TODO Refactor to remove warnings NP-49938
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class SampleExpandedPublicationFactory {
  private static final String ROLE_CREATOR = "Creator";
  private static final String ROLE_OTHER = "SomeOtherRole";

  private final List<SampleExpandedContributor> contributors = new ArrayList<>();
  private final List<SampleExpandedOrganization> topLevelOrganizations = new ArrayList<>();
  private final List<CustomerDto> customerOrganizations = new ArrayList<>();
  private final List<SampleExpandedPublicationChannel> publicationChannels = new ArrayList<>();
  private final Map<String, SampleExpandedPublicationChannel> channels = new HashMap<>();
  private final UUID publicationIdentifier = randomUUID();
  private final URI publicationId = generatePublicationId(publicationIdentifier);
  private String publicationType = "AcademicArticle";
  private PublicationDate publicationDate = randomPublicationDateInCurrentYear();
  private Collection<String> isbnList = List.of(randomIsbn13());
  private String revisionStatus = "Unrevised";

  public SampleExpandedPublicationFactory(TestScenario scenario) {
    // TODO: Use scenario?
  }

  public static SampleExpandedPublicationFactory defaultExpandedPublicationFactory(
      TestScenario scenario) {
    var factory = new SampleExpandedPublicationFactory(scenario);
    var nviOrganization1 = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    var nviOrganization2 = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    var nonNviOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_SWEDEN, false);

    return factory
        .withCreatorAffiliatedWith(nviOrganization1)
        .withCreatorAffiliatedWith(nviOrganization2.hasPart())
        .withNonCreatorsAffiliatedWith(1, nviOrganization1)
        .withNonCreatorsAffiliatedWith(1, nonNviOrganization)
        .withCreatorsAffiliatedWith(1, nonNviOrganization);
  }

  public URI getPublicationId() {
    return publicationId;
  }

  public List<CustomerDto> getCustomerOrganizations() {
    return List.copyOf(customerOrganizations);
  }

  public SampleExpandedPublicationFactory withPublicationType(String publicationType) {
    this.publicationType = publicationType;
    return this;
  }

  public SampleExpandedPublicationFactory withPublicationDate(PublicationDate publicationDate) {
    this.publicationDate = publicationDate;
    return this;
  }

  public SampleExpandedPublicationFactory withTopLevelOrganizations(
      Collection<Organization> topLevelOrganizations) {
    for (Organization organization : topLevelOrganizations) {
      this.topLevelOrganizations.add(createExpandedOrganization(organization, true));
    }
    return this;
  }

  private static SampleExpandedOrganization createExpandedOrganization(
      Organization organization, boolean isTopLevel) {
    var builder =
        SampleExpandedOrganization.builder()
            .withId(organization.id())
            .withType()
            .withCountryCode(organization.countryCode())
            .withLabels(organization.labels());

    if (!isTopLevel) {
      builder.withParentOrganizations(
          organization.partOf().stream().map(Organization::id).toList());
    }

    if (hasElements(organization.hasPart())) {
      var subOrganizations =
          organization.hasPart().stream()
              .map(sub -> createExpandedOrganization(sub, false))
              .toArray(SampleExpandedOrganization[]::new);
      builder.withSubOrganizations(subOrganizations);
    }

    return builder.build();
  }

  private void addContributor(
      URI id, String name, String role, Collection<Organization> affiliations) {
    var verificationStatus = nonNull(id) ? "Verified" : "NotVerified";
    var expandedAffiliations =
        affiliations.stream()
            .map(SampleExpandedPublicationFactory::mapOrganizationToAffiliation)
            .toList();
    var expandedContributor =
        SampleExpandedContributor.builder()
            .withId(id)
            .withNames(nonNull(name) ? List.of(name) : emptyList())
            .withRole(role)
            .withOrcId(randomString())
            .withVerificationStatus(verificationStatus)
            .withAffiliations(expandedAffiliations)
            .build();
    this.contributors.add(expandedContributor);
  }

  private void addContributor(String name, String role, Collection<Organization> affiliations) {
    addContributor(randomUriWithSuffix("creator"), name, role, affiliations);
  }

  public static SampleExpandedAffiliation mapOrganizationToAffiliation(Organization organization) {
    var topLevelId = organization.getTopLevelOrg().id();
    var builder =
        SampleExpandedAffiliation.builder()
            .withId(organization.id())
            .withCountryCode(organization.countryCode())
            .withLabels(organization.labels());
    if (isNull(organization.id()) || organization.id().equals(topLevelId)) {
      return builder.build();
    } else {
      return builder
          .withPartOf(organization.partOf().stream().map(Organization::id).toList())
          .build();
    }
  }

  private void addContributor(ContributorDto contributor, String... additionalNames) {
    var expandedAffiliations =
        contributor.affiliations().stream()
            .map(SampleExpandedPublicationFactory::mapOrganizationToAffiliation)
            .toList();
    var names = new ArrayList<String>();
    if (nonNull(contributor.name())) {
      names.add(contributor.name());
    }
    if (nonNull(additionalNames)) {
      names.addAll(List.of(additionalNames));
    }
    var expandedContributor =
        SampleExpandedContributor.builder()
            .withId(contributor.id())
            .withNames(names)
            .withRole(contributor.role().getValue())
            .withOrcId(randomString())
            .withVerificationStatus(contributor.verificationStatus().getValue())
            .withAffiliations(expandedAffiliations)
            .build();
    this.contributors.add(expandedContributor);
  }

  public SampleExpandedPublicationFactory withContributor(
      ContributorDto contributor, String... additionalNames) {
    this.addContributor(contributor, additionalNames);
    return this;
  }

  public SampleExpandedPublicationFactory withContributors(
      Collection<ContributorDto> contributors) {
    for (var contributor : contributors) {
      this.addContributor(contributor);
    }
    return this;
  }

  public SampleExpandedPublicationFactory withContributor(SampleExpandedContributor contributor) {
    this.contributors.add(contributor);
    return this;
  }

  public SampleExpandedPublicationFactory withCreatorAffiliatedWith(
      Collection<Organization> affiliations) {
    addContributor(null, ROLE_CREATOR, affiliations);
    return this;
  }

  public SampleExpandedPublicationFactory withCreatorAffiliatedWith(Organization... affiliations) {
    addContributor(null, ROLE_CREATOR, List.of(affiliations));
    return this;
  }

  public SampleExpandedPublicationFactory withCreatorsAffiliatedWith(
      int count, Organization... affiliations) {
    for (int i = 0; i < count; i++) {
      addContributor(randomString(), ROLE_CREATOR, List.of(affiliations));
    }
    return this;
  }

  public SampleExpandedPublicationFactory withNonCreatorAffiliatedWith(
      Organization... affiliations) {
    addContributor(null, ROLE_OTHER, List.of(affiliations));
    return this;
  }

  public SampleExpandedPublicationFactory withNonCreatorsAffiliatedWith(
      int count, Organization... affiliations) {
    for (int i = 0; i < count; i++) {
      addContributor(randomString(), ROLE_OTHER, List.of(affiliations));
    }
    return this;
  }

  public SampleExpandedPublicationFactory withIsbnList(Collection<String> isbnList) {
    this.isbnList = isbnList;
    return this;
  }

  public SampleExpandedPublicationFactory withRevisionStatus(String revisionStatus) {
    this.revisionStatus = revisionStatus;
    return this;
  }

  public SampleExpandedPublicationFactory withPublicationChannel(
      String channelType, String scientificLevel) {
    this.addPublicationChannel(channelType, scientificLevel);
    return this;
  }

  private void addPublicationChannel(String channelType, String scientificLevel) {
    var channel =
        SampleExpandedPublicationChannel.builder()
            .withId(randomUriWithSuffix("channel"))
            .withType(channelType)
            .withLevel(scientificLevel)
            .build();
    this.publicationChannels.add(channel);
    this.channels.put(channelType, channel);
  }

  public SampleExpandedPublication getExpandedPublication() {
    return getExpandedPublicationBuilder().build();
  }

  public SampleExpandedPublication.Builder getExpandedPublicationBuilder() {
    // Add default publication channel if none is set
    if (publicationChannels.isEmpty()) {
      addPublicationChannel(JOURNAL_TYPE, LEVEL_ONE);
    }
    var expandedDate =
        new SampleExpandedPublicationDate(
            publicationDate.year(), publicationDate.month(), publicationDate.day());

    return SampleExpandedPublication.builder()
        .withId(publicationId)
        .withIdentifier(publicationIdentifier)
        .withInstanceType(publicationType)
        .withPublicationDate(expandedDate)
        .withPublicationContext(resolvePublicationContext())
        .withContributors(contributors)
        .withTopLevelOrganizations(topLevelOrganizations);
  }

  private SampleExpandedPublicationContext resolvePublicationContext() {
    if (ACADEMIC_CHAPTER.equals(publicationType)) {
      return SampleExpandedPublicationContext.createAnthologyContext(
          channels, isbnList, randomUri(), randomString(), revisionStatus);
    }
    return SampleExpandedPublicationContext.createFlatPublicationContext(
        "Book", channels, isbnList, revisionStatus);
  }

  public Organization setupTopLevelOrganization(String countryCode, boolean isNviOrganization) {
    var topLevelOrganization = randomOrganization(countryCode, 2).build();
    this.topLevelOrganizations.add(createExpandedOrganization(topLevelOrganization, true));
    this.customerOrganizations.add(createCustomer(topLevelOrganization.id(), isNviOrganization));
    return topLevelOrganization;
  }
}
