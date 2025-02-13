package no.sikt.nva.nvi.test;

import static no.sikt.nva.nvi.test.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.requests.UpdateNonCandidateRequest;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
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

  public static DbApprovalStatus randomApproval(URI institutionId) {
    return new DbApprovalStatus(
        institutionId,
        randomElement(DbStatus.values()),
        randomUsername(),
        randomUsername(),
        randomInstant(),
        randomString());
  }

  private static Username randomUsername() {
    return Username.fromString(randomString());
  }

  public static DbApprovalStatus randomApproval() {
    return randomApproval(randomUri());
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

  public static UpdateNonCandidateRequest createUpsertNonCandidateRequest(URI publicationId) {
    return () -> publicationId;
  }

  public static UpdateStatusRequest createUpdateStatusRequest(
      ApprovalStatus status, URI institutionId, String username) {
    return UpdateStatusRequest.builder()
        .withReason(ApprovalStatus.REJECTED.equals(status) ? randomString() : null)
        .withApprovalStatus(status)
        .withInstitutionId(institutionId)
        .withUsername(username)
        .build();
  }

  public static UpsertRequestBuilder createUpsertCandidateRequest(URI... institutions) {
    var creators =
        IntStream.of(1)
            .mapToObj(i -> randomUri())
            .collect(Collectors.toMap(Function.identity(), e -> List.of(institutions)));
    var verifiedCreatorsAsDto =
        creators.entrySet().stream()
            .map(entry -> new VerifiedNviCreatorDto(entry.getKey(), entry.getValue()))
            .toList();

    var points =
        Arrays.stream(institutions)
            .map(
                institution -> {
                  var institutionPoints = randomBigDecimal();
                  return new InstitutionPoints(
                      institution,
                      institutionPoints,
                      creators.keySet().stream()
                          .map(
                              creator ->
                                  new CreatorAffiliationPoints(
                                      creator, institution, institutionPoints))
                          .toList());
                })
            .toList();

    return randomUpsertRequestBuilder()
        .withVerifiedCreators(verifiedCreatorsAsDto)
        .withCreators(creators)
        .withPoints(points);
  }

  public static UpsertCandidateRequest createUpsertCandidateRequest(
      URI topLevelOrg, URI affiliation) {
    var creatorId = randomUri();
    var creators = Map.of(creatorId, List.of(affiliation));
    var verifiedCreators = List.of(new VerifiedNviCreatorDto(creatorId, List.of(affiliation)));
    var points = randomBigDecimal();
    var institutionPoints =
        List.of(
            new InstitutionPoints(
                topLevelOrg,
                points,
                List.of(new CreatorAffiliationPoints(creatorId, affiliation, points))));

    return randomUpsertRequestBuilder()
        .withCreators(creators)
        .withVerifiedCreators(verifiedCreators)
        .withPoints(institutionPoints)
        .build();
  }

  public static HttpResponse<String> createResponse(int status, String body) {
    var response = (HttpResponse<String>) mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    return response;
  }
}
