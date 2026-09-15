package no.sikt.nva.nvi.common.client;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Optional;
import no.sikt.nva.nvi.common.client.model.PublicationChannelResponse;
import no.unit.nva.auth.uriretriever.UriRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: NP-51402 - Remove after data is migrated
public class PublicationChannelRetriever {

  private static final Logger LOGGER = LoggerFactory.getLogger(PublicationChannelRetriever.class);
  private static final String APPLICATION_JSON = "application/json";
  private static final String ERROR_COULD_NOT_FETCH_CHANNEL =
      "Could not fetch publication channel {}. Response code: {}";
  private static final String ERROR_COULD_NOT_PARSE_CHANNEL =
      "Could not parse publication channel {}";
  private static final String ERROR_NO_RESPONSE = "Got no response for publication channel {}";

  private final UriRetriever uriRetriever;

  public PublicationChannelRetriever(UriRetriever uriRetriever) {
    this.uriRetriever = uriRetriever;
  }

  public Optional<PublicationChannelResponse> fetchChannel(URI channelId) {
    return Optional.ofNullable(uriRetriever.fetchResponse(channelId, APPLICATION_JSON))
        .flatMap(response -> response)
        .or(() -> logMissingResponse(channelId))
        .filter(response -> isHttpOk(response, channelId))
        .flatMap(response -> toPublicationChannel(response.body(), channelId));
  }

  private static Optional<HttpResponse<String>> logMissingResponse(URI channelId) {
    LOGGER.error(ERROR_NO_RESPONSE, channelId);
    return Optional.empty();
  }

  private static boolean isHttpOk(HttpResponse<String> response, URI channelId) {
    if (response.statusCode() == HttpURLConnection.HTTP_OK) {
      return true;
    }
    LOGGER.error(ERROR_COULD_NOT_FETCH_CHANNEL, channelId, response.statusCode());
    return false;
  }

  private static Optional<PublicationChannelResponse> toPublicationChannel(
      String body, URI channelId) {
    return attempt(() -> dtoObjectMapper.readValue(body, PublicationChannelResponse.class))
        .toOptional(failure -> LOGGER.error(ERROR_COULD_NOT_PARSE_CHANNEL, channelId));
  }
}
