package no.sikt.nva.nvi.events.evaluator.calculator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.StringUtils.isBlank;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.events.evaluator.model.CustomerResponse;
import no.sikt.nva.nvi.events.evaluator.model.NviCreator;
import no.sikt.nva.nvi.events.evaluator.model.NviOrganization;
import no.sikt.nva.nvi.events.evaluator.model.UnverifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreatorVerificationUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreatorVerificationUtil.class);
  private static final String CONTENT_TYPE = "application/json";
  private static final String FAILED_TO_FETCH_CUSTOMER_MESSAGE =
      "Failed to fetch customer for %s (status code: %d)";
  private static final String CUSTOMER = "customer";
  private static final String CRISTIN_ID = "cristinId";
  private static final String API_HOST = new Environment().readEnv("API_HOST");
  private static final String COUNTRY_CODE_NORWAY = "NO";
  private final AuthorizedBackendUriRetriever authorizedBackendUriRetriever;

  public CreatorVerificationUtil(AuthorizedBackendUriRetriever authorizedBackendUriRetriever) {
    this.authorizedBackendUriRetriever = authorizedBackendUriRetriever;
  }

  public static List<VerifiedNviCreator> getVerifiedCreators(Collection<NviCreator> creators) {
    return creators.stream()
        .filter(VerifiedNviCreator.class::isInstance)
        .map(VerifiedNviCreator.class::cast)
        .toList();
  }

  public static List<UnverifiedNviCreator> getUnverifiedCreators(Collection<NviCreator> creators) {
    return creators.stream()
        .filter(UnverifiedNviCreator.class::isInstance)
        .map(UnverifiedNviCreator.class::cast)
        .toList();
  }

  public List<NviCreator> getNviCreatorsWithNviInstitutions(PublicationDto publication) {
    return publication.contributors().stream()
        .filter(ContributorDto::isCreator)
        .filter(CreatorVerificationUtil::isValidContributor)
        .map(this::toNviCreator)
        .filter(CreatorVerificationUtil::isAffiliatedWithNviOrganization)
        .toList();
  }

  private static URI createCustomerApiUri(String institutionId) {
    var getCustomerEndpoint =
        UriWrapper.fromHost(API_HOST).addChild(CUSTOMER).addChild(CRISTIN_ID).getUri();
    // Note: This is an odd way to encode the URI, but it may be necessary because of how this is
    // parsed
    // by the GetCustomerByCristinIdHandler in nva-identity-service. Check that it still works in
    // prod if
    // this is changed.
    return URI.create(
        getCustomerEndpoint + "/" + URLEncoder.encode(institutionId, StandardCharsets.UTF_8));
  }

  private static boolean isValidContributor(ContributorDto contributorDto) {
    return contributorDto.isVerified() || contributorDto.isNamed();
  }

  private static boolean isAffiliatedWithNviOrganization(NviCreator creator) {
    return !creator.nviAffiliations().isEmpty();
  }

  private static boolean isHttpOk(HttpResponse<String> response) {
    return response.statusCode() == HttpURLConnection.HTTP_OK;
  }

  private static boolean isNotFound(HttpResponse<String> response) {
    return response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND;
  }

  private static NviOrganization toNviOrganization(Organization organization) {
    return NviOrganization.builder()
        .withId(organization.id())
        .withTopLevelOrganization(
            NviOrganization.builder().withId(organization.getTopLevelOrg().id()).build())
        .build();
  }

  private static boolean hasRelevantCountryCode(Organization organization) {
    // We only need to check the affiliation if the country code is set to `NO` or missing.
    // Otherwise, we can skip it and not check if it is an NVI institution, saving us a network
    // call.
    return isBlank(organization.countryCode())
        || COUNTRY_CODE_NORWAY.equalsIgnoreCase(organization.countryCode());
  }

  private NviCreator toNviCreator(ContributorDto contributor) {
    if (contributor.isVerified()) {
      return toVerifiedNviCreator(contributor);
    }
    return toUnverifiedNviCreator(contributor);
  }

  private VerifiedNviCreator toVerifiedNviCreator(ContributorDto contributor) {
    return VerifiedNviCreator.builder()
        .withId(contributor.id())
        .withNviAffiliations(getNviAffiliationsIfExist(contributor))
        .build();
  }

  private UnverifiedNviCreator toUnverifiedNviCreator(ContributorDto contributor) {
    return UnverifiedNviCreator.builder()
        .withName(contributor.name())
        .withNviAffiliations(getNviAffiliationsIfExist(contributor))
        .build();
  }

  private List<NviOrganization> getNviAffiliationsIfExist(ContributorDto contributor) {
    return contributor.affiliations().stream()
        .filter(CreatorVerificationUtil::hasRelevantCountryCode)
        .filter(this::topLevelOrgIsNviInstitution)
        .map(CreatorVerificationUtil::toNviOrganization)
        .toList();
  }

  private boolean topLevelOrgIsNviInstitution(Organization organization) {
    return isNviInstitution(organization.getTopLevelOrg().id());
  }

  private static boolean mapToNviInstitutionValue(HttpResponse<String> response) {
    var body = response.body();
    try {
      var customerResponse = dtoObjectMapper.readValue(body, CustomerResponse.class);
      return customerResponse.nviInstitution();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isNviInstitution(URI institutionId) {
    var customerApiUri = createCustomerApiUri(institutionId.toString());
    var response = getResponse(customerApiUri);
    if (isHttpOk(response)) {
      return mapToNviInstitutionValue(response);
    }
    if (isNotFound(response)) {
      return false;
    }
    var message =
        String.format(FAILED_TO_FETCH_CUSTOMER_MESSAGE, customerApiUri, response.statusCode());
    LOGGER.error(message);
    throw new RuntimeException(message);
  }

  private HttpResponse<String> getResponse(URI uri) {
    return Optional.ofNullable(authorizedBackendUriRetriever.fetchResponse(uri, CONTENT_TYPE))
        .stream()
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findAny()
        .orElseThrow();
  }
}
