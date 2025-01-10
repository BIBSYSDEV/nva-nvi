package no.sikt.nva.nvi.events.evaluator.calculator;

import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static nva.commons.core.StringUtils.isBlank;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.events.evaluator.dto.AffiliationDto;
import no.sikt.nva.nvi.events.evaluator.dto.ContributorDto;
import no.sikt.nva.nvi.events.evaluator.model.NviOrganization;
import no.sikt.nva.nvi.events.evaluator.model.UnverifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreatorVerificationUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreatorVerificationUtil.class);
    private static final String CREATOR = "Creator";
    private static final String CONTENT_TYPE = "application/json";
    private static final String FAILED_TO_FETCH_CUSTOMER_MESSAGE = "Failed to fetch customer for %s (status code: %d)";
    private static final String CUSTOMER = "customer";
    private static final String CRISTIN_ID = "cristinId";
    private static final String VERIFIED = "Verified";
    private static final String API_HOST = new Environment().readEnv("API_HOST");
    private static final String COUNTRY_CODE_NORWAY = "NO";
    private final AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
    private final OrganizationRetriever organizationRetriever;

    public CreatorVerificationUtil(AuthorizedBackendUriRetriever authorizedBackendUriRetriever,
                                   UriRetriever uriRetriever) {
        this.authorizedBackendUriRetriever = authorizedBackendUriRetriever;
        this.organizationRetriever = new OrganizationRetriever(uriRetriever);
    }

    public static List<ContributorDto> extractContributors(JsonNode body) {
        return getStreamOfContributorNodes(body)
                   .map(ContributorDto::fromJsonNode)
                   .toList();
    }

    public List<VerifiedNviCreator> getVerifiedCreatorsWithNviInstitutions(List<ContributorDto> contributors) {
        return contributors
                   .stream()
                   .filter(CreatorVerificationUtil::isVerified)
                   .filter(CreatorVerificationUtil::isCreator)
                   .map(this::toVerifiedNviCreator)
                   .filter(CreatorVerificationUtil::isAffiliatedWithNviOrganization)
                   .toList();
    }

    public List<UnverifiedNviCreator> getUnverifiedCreatorsWithNviInstitutions(List<ContributorDto> contributors) {
        return contributors
                   .stream()
                   .filter(not(CreatorVerificationUtil::isVerified))
                   .filter(CreatorVerificationUtil::isCreator)
                   .filter(not(contributor -> isBlank(contributor.name())))
                   .map(this::toUnverifiedNviCreator)
                   .filter(CreatorVerificationUtil::isAffiliatedWithNviOrganization)
                   .toList();
    }

    private static boolean isAffiliatedWithNviOrganization(VerifiedNviCreator creator) {
        return !creator
                    .nviAffiliations()
                    .isEmpty();
    }

    private static boolean isAffiliatedWithNviOrganization(UnverifiedNviCreator creator) {
        return !creator
                    .nviAffiliations()
                    .isEmpty();
    }

    private static URI createCustomerApiUri(String institutionId) {
        var getCustomerEndpoint = UriWrapper
                                      .fromHost(API_HOST)
                                      .addChild(CUSTOMER)
                                      .addChild(CRISTIN_ID)
                                      .getUri();
        return URI.create(getCustomerEndpoint + "/" + URLEncoder.encode(institutionId, StandardCharsets.UTF_8));
    }

    private static boolean isVerified(ContributorDto contributor) {
        return VERIFIED.equals(contributor.verificationStatus());
    }

    private static boolean isCreator(ContributorDto contributor) {
        return CREATOR.equals(contributor.role());
    }

    private static boolean isHttpOk(HttpResponse<String> response) {
        return response.statusCode() == HttpURLConnection.HTTP_OK;
    }

    private static boolean isNotFound(HttpResponse<String> response) {
        return response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND;
    }

    private static Stream<JsonNode> getStreamOfContributorNodes(JsonNode body) {
        return StreamSupport.stream(body
                                        .at(JSON_PTR_CONTRIBUTOR)
                                        .spliterator(), false);
    }

    private static NviOrganization toNviOrganization(Organization organization) {
        return NviOrganization
                   .builder()
                   .withId(organization.id())
                   .withTopLevelOrganization(NviOrganization
                                                 .builder()
                                                 .withId(organization
                                                             .getTopLevelOrg()
                                                             .id())
                                                 .build())
                   .build();
    }

    private static boolean hasRelevantCountryCode(AffiliationDto expandedAffiliationDto) {
        // We only need to check the affiliation if the country code is set to `NO` or missing.
        // Otherwise, we can skip it and not check if it is an NVI institution, saving us a network call.
        return expandedAffiliationDto.countryCode() == null || COUNTRY_CODE_NORWAY.equalsIgnoreCase(
            expandedAffiliationDto.countryCode());
    }

    private VerifiedNviCreator toVerifiedNviCreator(ContributorDto contributor) {
        return VerifiedNviCreator
                   .builder()
                   .withId(contributor.id())
                   .withNviAffiliations(getNviAffiliationsIfExist(contributor))
                   .build();
    }

    private UnverifiedNviCreator toUnverifiedNviCreator(ContributorDto contributor) {
        return UnverifiedNviCreator
                   .builder()
                   .withName(contributor.name())
                   .withNviAffiliations(getNviAffiliationsIfExist(contributor))
                   .build();
    }

    private List<NviOrganization> getNviAffiliationsIfExist(ContributorDto contributor) {
        return contributor
                   .affiliations()
                   .stream()
                   .filter(CreatorVerificationUtil::hasRelevantCountryCode)
                   .map(AffiliationDto::id)
                   .distinct()
                   .map(organizationRetriever::fetchOrganization)
                   .filter(this::topLevelOrgIsNviInstitution)
                   .map(CreatorVerificationUtil::toNviOrganization)
                   .toList();
    }

    private boolean topLevelOrgIsNviInstitution(Organization organization) {
        return isNviInstitution(organization
                                    .getTopLevelOrg()
                                    .id());
    }

    private boolean isNviInstitution(URI institutionId) {
        var customerApiUri = createCustomerApiUri(institutionId.toString());
        var response = getResponse(customerApiUri);
        if (isHttpOk(response)) {
            return true;
        }
        if (isNotFound(response)) {
            return false;
        }
        var message = String.format(FAILED_TO_FETCH_CUSTOMER_MESSAGE, customerApiUri, response.statusCode());
        LOGGER.error(message);
        throw new RuntimeException(message);
    }

    private HttpResponse<String> getResponse(URI uri) {
        return Optional
                   .ofNullable(authorizedBackendUriRetriever.fetchResponse(uri, CONTENT_TYPE))
                   .stream()
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .findAny()
                   .orElseThrow();
    }
}
