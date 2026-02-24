package no.sikt.nva.nvi.test;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.ONE;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.unit.nva.testutils.RandomDataGenerator.FAKER;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.StringUtils.isNotBlank;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import nva.commons.core.paths.UriWrapper;

public final class TestUtils {

  public static final int SCALE = 4;
  public static final BigDecimal MIN_BIG_DECIMAL = BigDecimal.ZERO;
  public static final BigDecimal MAX_BIG_DECIMAL = BigDecimal.TEN;
  public static final int CURRENT_YEAR = getCurrentYear();
  public static final Random RANDOM = new Random();
  public static final AtomicInteger ID_COUNTER = new AtomicInteger(100);

  private static final LocalDate START_DATE = LocalDate.of(1970, 1, 1);
  private static final String PUBLICATION_API_PATH = "publication";
  private static final String API_HOST = "example.com";

  private TestUtils() {}

  public static int randomIntBetween(int min, int max) {
    return RANDOM.nextInt(min, max);
  }

  public static int generateUniqueId() {
    return ID_COUNTER.getAndIncrement();
  }

  public static String generateUniqueIdAsString() {
    return String.valueOf(generateUniqueId());
  }

  public static URI generatePublicationId(UUID identifier) {
    return UriWrapper.fromHost(API_HOST)
        .addChild(PUBLICATION_API_PATH)
        .addChild(identifier.toString())
        .getUri();
  }

  public static String randomYear() {
    return String.valueOf(randomIntBetween(START_DATE.getYear(), CURRENT_YEAR));
  }

  public static String randomTitle() {
    return String.format("%s %d", FAKER.book().title(), randomInteger(CURRENT_YEAR));
  }

  public static String randomInstitutionName() {
    var baseUnits = List.of("University of", "Institute for");
    var base = FAKER.options().option(baseUnits.toArray(String[]::new));
    return String.join(" ", base, FAKER.word().adjective(), FAKER.word().noun());
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

  public static ObjectNode createNodeWithType(String type) {
    var node = objectMapper.createObjectNode();
    node.put(TYPE_FIELD, type);
    return node;
  }

  public static <T> boolean hasElements(Collection<T> collection) {
    return nonNull(collection) && !collection.isEmpty();
  }

  public static <K, V> boolean hasElements(Map<K, V> collection) {
    return nonNull(collection) && !collection.isEmpty();
  }

  public static void putIfNotBlank(ObjectNode node, String field, String value) {
    if (isNotBlank(value)) {
      node.put(field, value);
    }
  }

  public static void putIfNotNull(ObjectNode node, String field, URI value) {
    if (nonNull(value)) {
      node.put(field, value.toString());
    }
  }

  public static void putAsArrayIfMultipleValues(
      ObjectNode node, String field, Collection<String> values) {
    if (hasElements(values)) {
      if (values.size() > ONE) {
        var arrayNode = objectMapper.createArrayNode();
        values.forEach(arrayNode::add);
        node.set(field, arrayNode);
      } else {
        putIfNotBlank(node, field, values.iterator().next());
      }
    }
  }

  public static void putAsArray(ObjectNode node, String field, Collection<String> values) {
    var arrayNode = objectMapper.createArrayNode();
    values.forEach(arrayNode::add);
    node.set(field, arrayNode);
  }

  private static int getCurrentYear() {
    return Year.now(ZoneId.of("Europe/Oslo")).getValue();
  }
}
