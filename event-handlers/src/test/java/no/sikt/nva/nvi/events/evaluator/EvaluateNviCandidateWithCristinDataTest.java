package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.createNviCustomer;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
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

  @BeforeEach
  void setup() {
    mockCustomerApi();
    setupOpenPeriod(scenario, "2022");
  }

  @Test
  void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicArticleFrom2022()
      throws IOException {
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

  private SQSEvent setupSqsEvent(String path) throws IOException {
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    return createEvent(new PersistedResourceMessage(fileUri));
  }

  private void mockCustomerApi() {
    var customers =
        List.of(
            createNviCustomer(NTNU_TOP_LEVEL_ORG_ID),
            createNviCustomer(ST_OLAVS_TOP_LEVEL_ORG_ID),
            createNviCustomer(UIO_TOP_LEVEL_ORG_ID),
            createNviCustomer(SINTEF_TOP_LEVEL_ORG_ID));
    mockGetAllCustomersResponse(customers);
  }

  private UpsertNviCandidateRequest getMessageBody() {
    var sentMessages = queueClient.getSentMessages();
    assertThat(sentMessages, hasSize(1));
    var message = sentMessages.getFirst();
    var candidateEvaluatedMessage =
        attempt(
                () ->
                    objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class))
            .orElseThrow();
    return (UpsertNviCandidateRequest) candidateEvaluatedMessage.candidate();
  }
}
