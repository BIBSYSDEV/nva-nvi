package no.sikt.nva.nvi.common;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.setupRandomOrganizationWithSubUnits;
import static no.sikt.nva.nvi.test.TestConstants.CHANNEL_PUBLISHER;
import static no.sikt.nva.nvi.test.TestConstants.CHANNEL_SERIES;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_JSON_PUBLICATION_DATE;
import static no.sikt.nva.nvi.test.TestUtils.createResponse;
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
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.test.SampleExpandedAffiliation;
import no.sikt.nva.nvi.test.SampleExpandedContributor;
import no.sikt.nva.nvi.test.SampleExpandedOrganization;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.sikt.nva.nvi.test.SampleExpandedPublicationChannel;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.UriRetriever;

public class SampleExpandedPublicationFactory {
  private static final String ROLE_CREATOR = "Creator";
  private static final String ROLE_OTHER = "SomeOtherRole";

  private static final URI CUSTOMER_API =
      URI.create("https://api.dev.nva.aws.unit.no/customer/cristinId");
  private final AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
  private final UriRetriever uriRetriever;
  private final List<SampleExpandedContributor> contributors = new ArrayList<>();
  private final List<SampleExpandedOrganization> topLevelOrganizations = new ArrayList<>();
  private final List<SampleExpandedPublicationChannel> publicationChannels = new ArrayList<>();
  private String publicationType = "AcademicArticle";

  public SampleExpandedPublicationFactory(
      AuthorizedBackendUriRetriever authorizedBackendUriRetriever, UriRetriever uriRetriever) {
    this.authorizedBackendUriRetriever = authorizedBackendUriRetriever;
    this.uriRetriever = uriRetriever;
  }

  public SampleExpandedPublicationFactory withPublicationType(String publicationType) {
    this.publicationType = publicationType;
    return this;
  }

  public SampleExpandedPublicationFactory withTopLevelOrganizations(
      Organization... topLevelOrganizations) {
    for (Organization organization : topLevelOrganizations) {
      addTopLevelOrganization(organization, organization.countryCode());
    }
    return this;
  }

  private void addTopLevelOrganization(Organization topLevelOrganization, String countryCode) {
    var subUnitIds = topLevelOrganization.hasPart().stream().map(Organization::id).toList();
    var expandedSubOrganizations = new ArrayList<SampleExpandedOrganization>();
    for (URI subUnitId : subUnitIds) {
      expandedSubOrganizations.add(
          createSubOrganization(subUnitId, topLevelOrganization.id(), countryCode));
    }
    var expandedTopLevelOrganization =
        SampleExpandedOrganization.builder()
            .withId(topLevelOrganization.id())
            .withCountryCode(countryCode)
            .withSubOrganizations(
                expandedSubOrganizations.toArray(SampleExpandedOrganization[]::new))
            .build();
    this.topLevelOrganizations.add(expandedTopLevelOrganization);
  }

  private static SampleExpandedOrganization createSubOrganization(
      URI subUnitId, URI topLevelId, String countryCode) {
    return SampleExpandedOrganization.builder()
        .withId(subUnitId)
        .withParentOrganizations(topLevelId)
        .withCountryCode(countryCode)
        .build();
  }

  private void mockCustomerApiResponseForNviInstitution(URI toplevelOrganizationId) {
    var okResponse = createResponse(200, "{\"nviInstitution\" : \"true\"}");
    var customerApiUriForOrganization = getCustomerApiUri(toplevelOrganizationId);
    when(authorizedBackendUriRetriever.fetchResponse(eq(customerApiUriForOrganization), any()))
        .thenReturn(Optional.of(okResponse));
  }

  private URI getCustomerApiUri(URI organizationId) {
    var parameterId = URLEncoder.encode(organizationId.toString(), StandardCharsets.UTF_8);
    return URI.create(CUSTOMER_API + "/" + parameterId);
  }

  private void addContributor(
      String name, String role, String countryCode, Collection<Organization> affiliations) {
    var expandedAffiliations =
        affiliations.stream()
            .map(organization -> mapOrganizationToAffiliation(organization, countryCode))
            .toList();
    var expandedContributor =
        SampleExpandedContributor.builder()
            .withId(randomUriWithSuffix("nviCreator"))
            .withNames(nonNull(name) ? List.of(name) : emptyList())
            .withRole(role)
            .withOrcId(randomString())
            .withVerificationStatus("Verified")
            .withAffiliations(expandedAffiliations)
            .build();
    this.contributors.add(expandedContributor);
  }

  private SampleExpandedAffiliation mapOrganizationToAffiliation(
      Organization organization, String countryCode) {
    var topLevelId = organization.getTopLevelOrg().id();
    if (isNull(organization.id()) || organization.id().equals(topLevelId)) {
      return SampleExpandedAffiliation.builder()
          .withId(organization.id())
          .withCountryCode(countryCode)
          .build();
    } else {
      return SampleExpandedAffiliation.builder()
          .withId(organization.id())
          .withCountryCode(countryCode)
          .withPartOf(organization.partOf().stream().map(Organization::id).toList())
          .build();
    }
  }

  public SampleExpandedPublicationFactory withNorwegianCreatorAffiliatedWith(
      Collection<Organization> affiliations) {
    addContributor(null, ROLE_CREATOR, COUNTRY_CODE_NORWAY, affiliations);
    return this;
  }

  public SampleExpandedPublicationFactory withNorwegianCreatorAffiliatedWith(
      Organization... affiliations) {
    addContributor(null, ROLE_CREATOR, COUNTRY_CODE_NORWAY, List.of(affiliations));
    return this;
  }

  public SampleExpandedPublicationFactory withNonCreatorAffiliatedWith(
      String countryCode, Organization... affiliations) {
    addContributor(randomString(), ROLE_OTHER, countryCode, List.of(affiliations));
    return this;
  }

  public SampleExpandedPublicationFactory withCreatorAffiliatedWith(
      String countryCode, Organization... affiliations) {
    addContributor(randomString(), ROLE_CREATOR, countryCode, List.of(affiliations));
    return this;
  }

  public SampleExpandedPublicationFactory withRandomCreatorsAffiliatedWith(
      int count, String countryCode, Organization... affiliations) {
    for (int i = 0; i < count; i++) {
      addContributor(randomString(), ROLE_CREATOR, countryCode, List.of(affiliations));
    }
    return this;
  }

  public SampleExpandedPublicationFactory withRandomNonCreatorsAffiliatedWith(
      int count, String countryCode, Organization... affiliations) {
    for (int i = 0; i < count; i++) {
      addContributor(randomString(), ROLE_OTHER, countryCode, List.of(affiliations));
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
            .withType(channelType)
            .withLevel(scientificLevel)
            .build();
    this.publicationChannels.add(channel);
  }

  public SampleExpandedPublication getExpandedPublication() {
    // Add default publication channel if none is set
    if (publicationChannels.isEmpty()) {
      addPublicationChannel("Journal", "LevelOne");
    }
    return getExpandedPublicationBuilder().build();
  }

  public SampleExpandedPublication.Builder getExpandedPublicationBuilder() {
    return SampleExpandedPublication.builder()
        .withInstanceType(publicationType)
        .withPublicationDate(HARDCODED_JSON_PUBLICATION_DATE)
        .withPublicationChannels(publicationChannels)
        .withContributors(contributors)
        .withTopLevelOrganizations(topLevelOrganizations);
  }

  public Organization setupTopLevelOrganization(String countryCode, boolean isNviOrganization) {
    var topLevelOrganization = setupRandomOrganizationWithSubUnits(countryCode, 2, uriRetriever);
    if (isNviOrganization) {
      mockCustomerApiResponseForNviInstitution(topLevelOrganization.id());
    } else {
      mockCustomerApiResponseForNonNviInstitution(topLevelOrganization.id());
    }
    return topLevelOrganization;
  }

  private void mockCustomerApiResponseForNonNviInstitution(URI toplevelOrganizationId) {
    var okResponse = createResponse(200, "{\"nviInstitution\" : \"false\"}");
    var customerApiUriForOrganization = getCustomerApiUri(toplevelOrganizationId);
    when(authorizedBackendUriRetriever.fetchResponse(eq(customerApiUriForOrganization), any()))
        .thenReturn(Optional.of(okResponse));
  }
}
