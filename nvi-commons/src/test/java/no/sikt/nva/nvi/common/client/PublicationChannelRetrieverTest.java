package no.sikt.nva.nvi.common.client;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import no.sikt.nva.nvi.test.uriretriever.FakeUriRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// TODO: NP-51402 - after data is migrated
class PublicationChannelRetrieverTest {

  private static final String APPLICATION_JSON = "application/json";
  private static final String CHANNEL_NAME = "Abasyn Journal of Life Sciences (AJLS)";
  private static final String CHANNEL_PRINT_ISSN = "2616-9754";
  private static final String CHANNEL_RESPONSE =
      """
      {
        "id" : "%s",
        "identifier" : "81543BCB-50FD-4277-8457-28E5674BA406",
        "name" : "%s",
        "onlineIssn" : "2663-1040",
        "printIssn" : "%s",
        "scientificValue" : "LevelOne",
        "sameAs" : "https://kanalregister.hkdir.no/publiseringskanaler/info/tidsskrift?pid=81543BCB-50FD-4277-8457-28E5674BA406",
        "type" : "Journal",
        "year" : "2024",
        "@context" : "https://bibsysdev.github.io/src/publication-channel/channel-context.json"
      }
      """;

  private FakeUriRetriever uriRetriever;
  private PublicationChannelRetriever channelRetriever;

  @BeforeEach
  void setUp() {
    uriRetriever = FakeUriRetriever.newInstance();
    channelRetriever = new PublicationChannelRetriever(uriRetriever);
  }

  @Test
  void shouldFetchNameAndPrintIssnForChannel() {
    var channelId = randomUri();
    registerChannelResponse(channelId, HTTP_OK);

    var channel = channelRetriever.fetchChannel(channelId);

    assertThat(channel).isPresent();
    assertThat(channel.orElseThrow().name()).isEqualTo(CHANNEL_NAME);
    assertThat(channel.orElseThrow().printIssn()).isEqualTo(CHANNEL_PRINT_ISSN);
  }

  @Test
  void shouldReturnEmptyWhenChannelIsNotFound() {
    var channelId = randomUri();
    registerChannelResponse(channelId, HTTP_NOT_FOUND);

    assertThat(channelRetriever.fetchChannel(channelId)).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenThereIsNoResponse() {
    assertThat(channelRetriever.fetchChannel(randomUri())).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenResponseCannotBeParsed() {
    var channelId = randomUri();
    uriRetriever.registerResponse(channelId, HTTP_OK, APPLICATION_JSON, "not json");

    assertThat(channelRetriever.fetchChannel(channelId)).isEmpty();
  }

  private void registerChannelResponse(URI channelId, int statusCode) {
    var body = CHANNEL_RESPONSE.formatted(channelId, CHANNEL_NAME, CHANNEL_PRINT_ISSN);
    uriRetriever.registerResponse(channelId, statusCode, APPLICATION_JSON, body);
  }
}
