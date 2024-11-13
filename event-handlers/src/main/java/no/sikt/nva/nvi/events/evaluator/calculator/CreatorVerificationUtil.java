package no.sikt.nva.nvi.events.evaluator.calculator;

import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_POINTER_IDENTITY_ID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_POINTER_IDENTITY_VERIFICATION_STATUS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ROLE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.streamNode;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.events.evaluator.model.CustomerResponse;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator.NviOrganization;
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
    private static final String COULD_NOT_FETCH_CUSTOMER_MESSAGE = "Could not fetch customer for: ";
    private static final String CUSTOMER = "customer";
    private static final String CRISTIN_ID = "cristinId";
    private static final String VERIFIED = "Verified";
    private static final String API_HOST = new Environment().readEnv("API_HOST");
    private final AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
    private final OrganizationRetriever organizationRetriever;

    public CreatorVerificationUtil(AuthorizedBackendUriRetriever authorizedBackendUriRetriever,
                                   UriRetriever uriRetriever) {
        this.authorizedBackendUriRetriever = authorizedBackendUriRetriever;
        this.organizationRetriever = new OrganizationRetriever(uriRetriever);
    }

    public List<VerifiedNviCreator> getVerifiedCreatorsWithNviInstitutionsIfExists(JsonNode publication) {
        var verifiedCreatorsWithNviInstitutions = getVerifiedCreatorsWithNviInstitutions(publication);

        return verifiedCreatorsWithNviInstitutions.isEmpty()
                   ? Collections.emptyList()
                   : verifiedCreatorsWithNviInstitutions;
    }

    private static boolean isAffiliatedWithNviOrganization(VerifiedNviCreator creator) {
        return !creator.nviAffiliations().isEmpty();
    }

    private static URI createCustomerApiUri(String institutionId) {
        var getCustomerEndpoint = UriWrapper.fromHost(API_HOST).addChild(CUSTOMER).addChild(CRISTIN_ID).getUri();
        return URI.create(getCustomerEndpoint + "/" + URLEncoder.encode(institutionId, StandardCharsets.UTF_8));
    }

    private static URI extractContributorId(JsonNode creatorNode) {
        return URI.create(extractJsonNodeTextValue(creatorNode, JSON_POINTER_IDENTITY_ID));
    }

    private static boolean isVerified(JsonNode contributorNode) {
        return VERIFIED.equals(extractJsonNodeTextValue(contributorNode, JSON_POINTER_IDENTITY_VERIFICATION_STATUS));
    }

    private static CustomerResponse toCustomer(String responseBody) {
        return attempt(() -> dtoObjectMapper.readValue(responseBody, CustomerResponse.class)).orElseThrow();
    }

    private static boolean isHttpOk(HttpResponse<String> response) {
        return response.statusCode() == HttpURLConnection.HTTP_OK;
    }

    private static boolean isNotFound(HttpResponse<String> response) {
        return response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND;
    }

    private static boolean isSuccessOrNotFound(HttpResponse<String> response) {
        return isHttpOk(response) || isNotFound(response);
    }

    private static boolean mapToNviInstitutionValue(HttpResponse<String> response) {
        return attempt(response::body).map(CreatorVerificationUtil::toCustomer)
                   .map(CustomerResponse::nviInstitution)
                   .orElse(failure -> false);
    }

    private static Stream<JsonNode> getJsonNodeStream(JsonNode jsonNode, String jsonPtr) {
        return StreamSupport.stream(jsonNode.at(jsonPtr).spliterator(), false);
    }

    private static boolean isCreator(JsonNode contributorNode) {
        return CREATOR.equals(extractJsonNodeTextValue(contributorNode, JSON_PTR_ROLE_TYPE));
    }

    private static NviOrganization toNviOrganization(Organization organization) {
        return NviOrganization.builder()
                   .withId(organization.id())
                   .withTopLevelOrganization(
                       NviOrganization.builder().withId(organization.getTopLevelOrg().id()).build())
                   .build();
    }

    private List<VerifiedNviCreator> getVerifiedCreatorsWithNviInstitutions(JsonNode body) {
        return getJsonNodeStream(body, JSON_PTR_CONTRIBUTOR)
                   .filter(CreatorVerificationUtil::isVerified)
                   .filter(CreatorVerificationUtil::isCreator)
                   .map(this::toVerifiedNviCreator)
                   .filter(CreatorVerificationUtil::isAffiliatedWithNviOrganization)
                   .toList();
    }

    private VerifiedNviCreator toVerifiedNviCreator(JsonNode contributorNode) {
        return VerifiedNviCreator.builder()
                   .withId(extractContributorId(contributorNode))
                   .withNviAffiliations(getNviAffiliationsIfExist(contributorNode))
                   .build();
    }

    private List<NviOrganization> getNviAffiliationsIfExist(JsonNode contributorNode) {
        return streamNode(contributorNode.at(JSON_PTR_AFFILIATIONS))
                   .map(JsonUtils::extractId)
                   .distinct()
                   .map(organizationRetriever::fetchOrganization)
                   .filter(this::topLevelOrgIsNviInstitution)
                   .map(CreatorVerificationUtil::toNviOrganization)
                   .toList();
    }

    private boolean topLevelOrgIsNviInstitution(Organization organization) {
        return isNviInstitution(organization.getTopLevelOrg().id());
    }

    private boolean isNviInstitution(URI institutionId) {
        var customerApiUri = createCustomerApiUri(institutionId.toString());
        var response = getResponse(customerApiUri);
        if (isSuccessOrNotFound(response)) {
            return mapToNviInstitutionValue(response);
        }
        LOGGER.error(COULD_NOT_FETCH_CUSTOMER_MESSAGE + customerApiUri + ". Response code: {}", response.statusCode());
        throw new RuntimeException(COULD_NOT_FETCH_CUSTOMER_MESSAGE + institutionId);
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
