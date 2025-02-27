package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestUtils.createResponse;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvaluateNviCandidateWithCristinDataTest extends EvaluationTest {

  private static final URI BASE_PATH =
      URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization");
  private static final URI NTNU_TOP_LEVEL_ORG_ID =
      UriWrapper.fromUri(BASE_PATH).addChild("194.0.0.0").getUri();
  private static final URI ST_OLAVS_TOP_LEVEL_ORG_ID =
      UriWrapper.fromUri(BASE_PATH).addChild("1920.0.0.0").getUri();
  private static final URI UIO_TOP_LEVEL_ORG_ID =
      UriWrapper.fromUri(BASE_PATH).addChild("185.90.0.0").getUri();
  private static final URI SINTEF_TOP_LEVEL_ORG_ID =
      UriWrapper.fromUri(BASE_PATH).addChild("7401.0.0.0").getUri();

  private static final String CUSTOMER = "customer";
  private static final String API_HOST = new Environment().readEnv("API_HOST");
  private static final String CRISTIN_ID = "cristinId";

  @BeforeEach
  void setup() {
    setupOpenPeriod(scenario, "2022");
  }

  @Test
  void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicArticleFrom2022()
      throws IOException {
    mockCristinApiResponsesForAllSubUnitsInAcademicArticle();
    mockCustomerApi();
    var event = setupSqsEvent("evaluator/cristin_candidate_2022_academicArticle.json");
    handler.handleRequest(event, CONTEXT);
    var candidate = getMessageBody();
    assertThat(
        getPointsForInstitution(candidate, NTNU_TOP_LEVEL_ORG_ID),
        is(equalTo(scaledBigDecimal(0.8165))));
    assertThat(
        getPointsForInstitution(candidate, ST_OLAVS_TOP_LEVEL_ORG_ID),
        is(equalTo(scaledBigDecimal(0.5774))));
  }

  @Test
  void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicMonographFrom2022()
      throws IOException {
    var subUnitId = UriWrapper.fromUri(BASE_PATH).addChild("185.15.13.55").getUri();
    mockOrganizationResponseForAffiliation(UIO_TOP_LEVEL_ORG_ID, subUnitId, uriRetriever);
    mockCristinResponseForNonNviOrganizationsForAcademicMonograph();
    mockCustomerApi(UIO_TOP_LEVEL_ORG_ID);

    var event = setupSqsEvent("evaluator/cristin_candidate_2022_academicMonograph.json");
    handler.handleRequest(event, CONTEXT);
    var candidate = getMessageBody();
    assertThat(
        getPointsForInstitution(candidate, UIO_TOP_LEVEL_ORG_ID),
        is(equalTo(scaledBigDecimal(3.7528))));
  }

  @Test
  void shouldReturnSamePointsAsPointsCalculatedByCristinForLiteratureReviewFrom2022()
      throws IOException {
    var subUnitId = UriWrapper.fromUri(BASE_PATH).addChild("194.65.15.0").getUri();
    mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, subUnitId, uriRetriever);
    mockCristinResponseForNonNviOrganizationsForLiteratureReview();
    mockCustomerApi(NTNU_TOP_LEVEL_ORG_ID);

    var event = setupSqsEvent("evaluator/cristin_candidate_2022_academicLiteratureReview.json");
    handler.handleRequest(event, CONTEXT);
    var candidate = getMessageBody();
    assertThat(
        getPointsForInstitution(candidate, NTNU_TOP_LEVEL_ORG_ID),
        is(equalTo(scaledBigDecimal(1.5922))));
  }

  @Test
  void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicChapterFrom2022()
      throws IOException {
    mockCristinApiResponsesForAllSubUnitsInAcademicChapter();
    mockCustomerApi(NTNU_TOP_LEVEL_ORG_ID);
    mockCustomerApi(SINTEF_TOP_LEVEL_ORG_ID);

    var event = setupSqsEvent("evaluator/cristin_candidate_2022_academicChapter.json");
    handler.handleRequest(event, CONTEXT);
    var candidate = getMessageBody();
    assertThat(
        getPointsForInstitution(candidate, NTNU_TOP_LEVEL_ORG_ID),
        is(equalTo(scaledBigDecimal(0.8660))));
    assertThat(
        getPointsForInstitution(candidate, SINTEF_TOP_LEVEL_ORG_ID),
        is(equalTo(scaledBigDecimal(0.5000))));
  }

  private static BigDecimal scaledBigDecimal(double val) {
    return BigDecimal.valueOf(val).setScale(SCALE, ROUNDING_MODE);
  }

  private static URI createCustomerApiUri(String institutionId) {
    var getCustomerEndpoint =
        UriWrapper.fromHost(API_HOST).addChild(CUSTOMER).addChild(CRISTIN_ID).getUri();
    return URI.create(
        getCustomerEndpoint + "/" + URLEncoder.encode(institutionId, StandardCharsets.UTF_8));
  }

  private void mockCristinResponseForNonNviOrganizationsForLiteratureReview() {
    var organization1 = UriWrapper.fromUri(BASE_PATH).addChild("13900000.0.0.0").getUri();
    var organization2 = UriWrapper.fromUri(BASE_PATH).addChild("13920157.0.0.0").getUri();
    mockOrganizationResponseForAffiliation(organization1, null, uriRetriever);
    mockOrganizationResponseForAffiliation(organization2, null, uriRetriever);
  }

  private void mockCristinResponseForNonNviOrganizationsForAcademicMonograph() {
    var organization1 = UriWrapper.fromUri(BASE_PATH).addChild("14100020.0.0.0").getUri();
    var organization2 = UriWrapper.fromUri(BASE_PATH).addChild("12300050.0.0.0").getUri();
    mockOrganizationResponseForAffiliation(organization1, null, uriRetriever);
    mockOrganizationResponseForAffiliation(organization2, null, uriRetriever);
  }

  private void mockCristinApiResponsesForAllSubUnitsInAcademicChapter() {
    var organization1 = UriWrapper.fromUri(BASE_PATH).addChild("194.64.94.0").getUri();
    var organization2 = UriWrapper.fromUri(BASE_PATH).addChild("194.64.45.0").getUri();
    var organization3 = UriWrapper.fromUri(BASE_PATH).addChild("7401.30.40.0").getUri();
    mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, organization1, uriRetriever);
    mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, organization2, uriRetriever);
    mockOrganizationResponseForAffiliation(SINTEF_TOP_LEVEL_ORG_ID, organization3, uriRetriever);
  }

  private SQSEvent setupSqsEvent(String path) throws IOException {
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    return createEvent(new PersistedResourceMessage(fileUri));
  }

  private void mockCristinApiResponsesForAllSubUnitsInAcademicArticle() {
    var organization1 = UriWrapper.fromUri(BASE_PATH).addChild("194.65.0.0").getUri();
    var organization2 = UriWrapper.fromUri(BASE_PATH).addChild("194.63.10.0").getUri();
    var organization3 = UriWrapper.fromUri(BASE_PATH).addChild("1920.13.0.0").getUri();
    var organization4 = UriWrapper.fromUri(BASE_PATH).addChild("194.65.25.0").getUri();
    mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, organization1, uriRetriever);
    mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, organization2, uriRetriever);
    mockOrganizationResponseForAffiliation(ST_OLAVS_TOP_LEVEL_ORG_ID, organization3, uriRetriever);
    mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, organization4, uriRetriever);
    mockOrganizationResponseForAffiliation(ST_OLAVS_TOP_LEVEL_ORG_ID, null, uriRetriever);
  }

  private void mockCustomerApi() {
    mockCustomerApi(NTNU_TOP_LEVEL_ORG_ID);
    mockCustomerApi(ST_OLAVS_TOP_LEVEL_ORG_ID);
  }

  private void mockCustomerApi(URI topLevelOrgId) {
    var customerApiResponse = createResponse(200, CUSTOMER_API_NVI_RESPONSE);
    when(authorizedBackendUriRetriever.fetchResponse(
            eq(createCustomerApiUri(topLevelOrgId.toString())), any()))
        .thenReturn(Optional.of(customerApiResponse));
  }

  private NviCandidate getMessageBody() {
    var sentMessages = queueClient.getSentMessages();
    assertThat(sentMessages, hasSize(1));
    var message = sentMessages.getFirst();
    var candidateEvaluatedMessage =
        attempt(
                () ->
                    objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class))
            .orElseThrow();
    return (NviCandidate) candidateEvaluatedMessage.candidate();
  }
}
