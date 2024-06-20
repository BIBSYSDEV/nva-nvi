package no.sikt.nva.nvi.common.client;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Optional;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.unit.nva.auth.uriretriever.UriRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrganizationRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrganizationRetriever.class);
    private static final String APPLICATION_JSON = "application/json";
    private static final String CRISTIN_VERSION = "; version=2023-05-26";
    private static final String MEDIA_TYPE_JSON_V2 = APPLICATION_JSON + CRISTIN_VERSION;
    private static final String COULD_NOT_FETCH_CRISTIN_ORG_MESSAGE = "Could not fetch Cristin organization for: ";
    private static final String ERROR_COULD_NOT_FETCH_CRISTIN_ORG = COULD_NOT_FETCH_CRISTIN_ORG_MESSAGE + "{}. "
                                                                    + "Response code: {}";

    private final UriRetriever uriRetriever;

    public OrganizationRetriever(UriRetriever uriRetriever) {
        this.uriRetriever = uriRetriever;
    }

    public Organization fetchOrganization(URI organizationId) {
        var response = getResponse(organizationId);
        if (isHttpOk(response)) {
            return toCristinOrganization(response.body());
        } else {
            LOGGER.error(ERROR_COULD_NOT_FETCH_CRISTIN_ORG, organizationId, response.statusCode());
            throw new RuntimeException(COULD_NOT_FETCH_CRISTIN_ORG_MESSAGE + organizationId);
        }
    }

    private static boolean isHttpOk(HttpResponse<String> response) {
        return response.statusCode() == HttpURLConnection.HTTP_OK;
    }

    private HttpResponse<String> getResponse(URI uri) {
        return Optional.ofNullable(uriRetriever.fetchResponse(uri, MEDIA_TYPE_JSON_V2))
                   .stream()
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .findAny()
                   .orElseThrow();
    }

    private Organization toCristinOrganization(String response) {
        return attempt(() -> dtoObjectMapper.readValue(response, Organization.class)).orElseThrow();
    }
}
