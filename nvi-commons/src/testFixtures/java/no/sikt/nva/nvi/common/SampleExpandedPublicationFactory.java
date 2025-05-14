package no.sikt.nva.nvi.common;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.setupRandomOrganization;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateInCurrentYear;
import static no.sikt.nva.nvi.test.TestConstants.CHANNEL_PUBLISHER;
import static no.sikt.nva.nvi.test.TestConstants.CHANNEL_SERIES;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static no.sikt.nva.nvi.test.TestUtils.createResponse;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.sikt.nva.nvi.test.SampleExpandedAffiliation;
import no.sikt.nva.nvi.test.SampleExpandedContributor;
import no.sikt.nva.nvi.test.SampleExpandedOrganization;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.sikt.nva.nvi.test.SampleExpandedPublicationChannel;
import no.sikt.nva.nvi.test.SampleExpandedPublicationDate;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public class SampleExpandedPublicationFactory {
  private static final String ROLE_CREATOR = "Creator";
  private static final String ROLE_OTHER = "SomeOtherRole";

  private final Environment environment;
  private final AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
  private final UriRetriever uriRetriever;
  private final List<SampleExpandedContributor> contributors = new ArrayList<>();
  private final List<SampleExpandedOrganization> topLevelOrganizations = new ArrayList<>();
  private final List<SampleExpandedPublicationChannel> publicationChannels = new ArrayList<>();
  private final UUID publicationIdentifier = randomUUID();
  private final URI publicationId = generatePublicationId(publicationIdentifier);
  private String publicationType = "AcademicArticle";
  private PublicationDate publicationDate = randomPublicationDateInCurrentYear();

  public SampleExpandedPublicationFactory(
      AuthorizedBackendUriRetriever authorizedBackendUriRetriever, UriRetriever uriRetriever) {
    this.environment = new Environment();
    this.authorizedBackendUriRetriever = authorizedBackendUriRetriever;
    this.uriRetriever = uriRetriever;
  }

  public SampleExpandedPublicationFactory(TestScenario scenario) {
    this.environment = scenario.getEnvironment();
    this.authorizedBackendUriRetriever = scenario.getAuthorizedBackendUriRetriever();
    this.uriRetriever = scenario.getUriRetriever();
  }

  public static SampleExpandedPublicationFactory defaultExpandedPublicationFactory(
      AuthorizedBackendUriRetriever authorizedBackendUriRetriever, UriRetriever uriRetriever) {
    var factory = new SampleExpandedPublicationFactory(authorizedBackendUriRetriever, uriRetriever);
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

  public SampleExpandedPublicationFactory withPublicationType(String publicationType) {
    this.publicationType = publicationType;
    return this;
  }

  public SampleExpandedPublicationFactory withPublicationDate(PublicationDate publicationDate) {
    this.publicationDate = publicationDate;
    return this;
  }

  public SampleExpandedPublicationFactory withTopLevelOrganizations(
      Organization... topLevelOrganizations) {
    for (Organization organization : topLevelOrganizations) {
      addTopLevelOrganization(organization);
    }
    return this;
  }

  private void addTopLevelOrganization(Organization topLevelOrganization) {
    var expandedSubOrganizations = new ArrayList<SampleExpandedOrganization>();
    for (var subOrganization : topLevelOrganization.hasPart()) {
      expandedSubOrganizations.add(createSubOrganization(subOrganization));
    }
    var expandedTopLevelOrganization =
        SampleExpandedOrganization.builder()
            .withId(topLevelOrganization.id())
            .withType()
            .withCountryCode(topLevelOrganization.countryCode())
            .withLabels(topLevelOrganization.labels())
            .withSubOrganizations(
                expandedSubOrganizations.toArray(SampleExpandedOrganization[]::new))
            .build();
    this.topLevelOrganizations.add(expandedTopLevelOrganization);
  }

  private static SampleExpandedOrganization createSubOrganization(Organization subOrganization) {
    return SampleExpandedOrganization.builder()
        .withId(subOrganization.id())
        .withType()
        .withParentOrganizations(subOrganization.partOf().stream().map(Organization::id).toList())
        .withCountryCode(subOrganization.countryCode())
        .withLabels(subOrganization.labels())
        .build();
  }

  private URI getCustomerApiUri(URI organizationId) {
    var getCustomerEndpoint =
        UriWrapper.fromHost(environment.readEnv("API_HOST"))
            .addChild("customer")
            .addChild("cristinId")
            .getUri();
    var parameterId = URLEncoder.encode(organizationId.toString(), StandardCharsets.UTF_8);
    return URI.create(getCustomerEndpoint + "/" + parameterId);
  }

  private void addContributor(
      URI id, String name, String role, Collection<Organization> affiliations) {
    var verificationStatus = nonNull(id) ? "Verified" : "Unverified";
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

  public SampleExpandedPublicationFactory withContributor(
      ContributorDto contributor, String... additionalNames) {
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

  /**
   * Adds a publication channel to the publication. If the channel type is "series" or "publisher",
   * it will add the corresponding channel with "Unassigned" as the scientific level. Valid
   * documents with one of these will have both channels, so this is a convenience method for that.
   * Use the underlying SampleExpandedPublication builder to override this behaviour.
   */
  public SampleExpandedPublicationFactory withPublicationChannel(
      String channelType, String scientificLevel) {
    this.addPublicationChannel(channelType, scientificLevel);
    if (CHANNEL_SERIES.equalsIgnoreCase(channelType)) {
      this.addPublicationChannel(CHANNEL_PUBLISHER, "Unassigned");
    }
    if (CHANNEL_PUBLISHER.equalsIgnoreCase(channelType)) {
      this.addPublicationChannel(CHANNEL_SERIES, "Unassigned");
    }
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
  }

  public SampleExpandedPublication getExpandedPublication() {
    return getExpandedPublicationBuilder().build();
  }

  public SampleExpandedPublication.Builder getExpandedPublicationBuilder() {
    // Add default publication channel if none is set
    if (publicationChannels.isEmpty()) {
      addPublicationChannel("Journal", "LevelOne");
    }
    var expandedDate =
        new SampleExpandedPublicationDate(
            publicationDate.year(), publicationDate.month(), publicationDate.day());

    return SampleExpandedPublication.builder()
        .withId(publicationId)
        .withIdentifier(publicationIdentifier)
        .withInstanceType(publicationType)
        .withPublicationDate(expandedDate)
        .withPublicationChannels(publicationChannels)
        .withContributors(contributors)
        .withTopLevelOrganizations(topLevelOrganizations);
  }

  public Organization setupTopLevelOrganization(String countryCode, boolean isNviOrganization) {
    var topLevelOrganization = setupRandomOrganization(countryCode, 2, uriRetriever);
    mockCustomerApiResponse(topLevelOrganization.id(), isNviOrganization);
    addTopLevelOrganization(topLevelOrganization);
    return topLevelOrganization;
  }

  private void mockCustomerApiResponse(URI toplevelOrganizationId, boolean isNviOrganization) {
    var responseBody = String.format("{\"nviInstitution\" : \"%s\"}", isNviOrganization);
    var okResponse = createResponse(200, responseBody);
    var customerApiUriForOrganization = getCustomerApiUri(toplevelOrganizationId);
    when(authorizedBackendUriRetriever.fetchResponse(eq(customerApiUriForOrganization), any()))
        .thenReturn(Optional.of(okResponse));
  }
}
