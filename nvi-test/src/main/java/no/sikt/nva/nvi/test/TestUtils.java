package no.sikt.nva.nvi.test;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.Year;
import java.util.Random;
import java.util.UUID;
import nva.commons.core.paths.UriWrapper;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.GodClass"})
public final class TestUtils {

  public static final int SCALE = 4;
  public static final BigDecimal MIN_BIG_DECIMAL = BigDecimal.ZERO;
  public static final BigDecimal MAX_BIG_DECIMAL = BigDecimal.TEN;
  public static final int CURRENT_YEAR = Year.now().getValue();
  public static final Random RANDOM = new Random();

  private static final String BUCKET_HOST = "example.org";
  private static final LocalDate START_DATE = LocalDate.of(1970, 1, 1);
  private static final String PUBLICATION_API_PATH = "publication";
  private static final String API_HOST = "example.com";

  private TestUtils() {}

  public static int randomIntBetween(int min, int max) {
    return RANDOM.nextInt(min, max);
  }

  public static URI generateS3BucketUri(UUID identifier) {
    return UriWrapper.fromHost(BUCKET_HOST).addChild(identifier.toString()).getUri();
  }

  public static URI generatePublicationId(UUID identifier) {
    return UriWrapper.fromHost(API_HOST)
        .addChild(PUBLICATION_API_PATH)
        .addChild(identifier.toString())
        .getUri();
  }

  public static String randomYear() {
    return String.valueOf(randomIntBetween(START_DATE.getYear(), LocalDate.now().getYear()));
  }

  public static URI randomUriWithSuffix(String suffix) {
    return UriWrapper.fromHost("https://example.org/")
        .addChild(randomString())
        .addChild(suffix)
        .getUri();
  }

  public static BigDecimal randomBigDecimal() {
    return randomBigDecimal(SCALE);
  }

  public static BigDecimal randomBigDecimal(int scale) {
    var randomBigDecimal =
        MIN_BIG_DECIMAL.add(
            BigDecimal.valueOf(Math.random()).multiply(MAX_BIG_DECIMAL.subtract(MIN_BIG_DECIMAL)));
    return randomBigDecimal.setScale(scale, RoundingMode.HALF_UP);
  }

  public static HttpResponse<String> createResponse(int status, String body) {
    var response = (HttpResponse<String>) mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    return response;
  }
}
