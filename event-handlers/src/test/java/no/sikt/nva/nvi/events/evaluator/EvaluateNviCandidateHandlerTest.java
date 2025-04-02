package no.sikt.nva.nvi.events.evaluator;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.setupReportedCandidate;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.common.service.model.InstanceType.ACADEMIC_COMMENTARY;
import static no.sikt.nva.nvi.common.service.model.InstanceType.ACADEMIC_LITERATURE_REVIEW;
import static no.sikt.nva.nvi.common.service.model.InstanceType.ACADEMIC_MONOGRAPH;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.events.evaluator.model.PublicationChannel.JOURNAL;
import static no.sikt.nva.nvi.events.evaluator.model.PublicationChannel.SERIES;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static no.sikt.nva.nvi.test.TestConstants.CRISTIN_NVI_ORG_SUB_UNIT_ID;
import static no.sikt.nva.nvi.test.TestConstants.CRISTIN_NVI_ORG_TOP_LEVEL_ID;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_CREATOR_ID;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_JSON_PUBLICATION_DATE;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_PUBLICATION_ID;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createResponse;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.model.PublicationDetails;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.evaluator.model.PublicationChannel;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.events.model.PublicationDate;
import no.sikt.nva.nvi.test.SampleExpandedAffiliation;
import no.sikt.nva.nvi.test.SampleExpandedContributor;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.sikt.nva.nvi.test.SampleExpandedPublicationChannel;
import no.sikt.nva.nvi.test.SampleExpandedPublicationDate;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings("PMD.CouplingBetweenObjects")
class EvaluateNviCandidateHandlerTest extends EvaluationTest {

  public static final PublicationDate HARDCODED_PUBLICATION_DATE =
      new PublicationDate(null, null, "2023");
  public static final URI HARDCODED_PUBLICATION_CHANNEL_ID =
      URI.create("https://api.dev.nva.aws.unit.no/publication-channels/series/490845/2023");
  public static final URI SIKT_CRISTIN_ORG_ID =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
  private static final String ACADEMIC_CHAPTER_PATH = "evaluator/candidate_academicChapter.json";

  private static final String ACADEMIC_LITERATURE_REVIEW_JSON_PATH =
      "evaluator/candidate_academicLiteratureReview" + ".json";
  private static final String ACADEMIC_MONOGRAPH_JSON_PATH =
      "evaluator/candidate_academicMonograph.json";
  private static final String ACADEMIC_COMMENTARY_JSON_PATH =
      "evaluator/candidate_academicCommentary.json";
  private static final String ACADEMIC_ARTICLE_PATH = "evaluator/candidate_academicArticle.json";
  private static final String ACADEMIC_ARTICLE =
      IoUtils.stringFromResources(Path.of(ACADEMIC_ARTICLE_PATH))
          .replace("__REPLACE_WITH_PUBLICATION_ID__", HARDCODED_PUBLICATION_ID.toString());
  private static final String ERROR_COULD_NOT_FETCH_CRISTIN_ORG =
      "Could not fetch Cristin organization for: ";
  private static final SampleExpandedAffiliation DEFAULT_SUBUNIT_AFFILIATION =
      SampleExpandedAffiliation.builder().withId(CRISTIN_NVI_ORG_SUB_UNIT_ID).build();
  private static final URI CUSTOMER_API_CRISTIN_NVI_ORG_TOP_LEVEL =
      URI.create(
          "https://api.dev.nva.aws.unit.no/customer/cristinId/https%3A%2F%2Fapi"
              + ".dev.nva.aws.unit.no%2Fcristin%2Forganization%2F194.0.0.0");
  private static final String NON_NVI_CUSTOMER_PATH = "nonNviCustomerResponse.json";

  @Test
  void shouldCreateNewCandidateEventOnValidCandidate() throws IOException {
    when(authorizedBackendUriRetriever.fetchResponse(any(), any()))
        .thenReturn(Optional.of(okResponse));
    mockOrganizationResponseForAffiliation(SIKT_CRISTIN_ORG_ID, null, uriRetriever);
    var path = "evaluator/candidate.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (NviCandidate) getMessageBody().candidate();
    assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
  }

  @Test
  void shouldSkipEvaluationAndLogWarningOnPublicationWithInvalidYear() throws IOException {
    var invalidYear = "1948-1997";
    var fileUri = setupPublicationWithInvalidYear(invalidYear);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    final var logAppender = LogUtils.getTestingAppender(EvaluatorService.class);
    handler.handleRequest(event, CONTEXT);
    var expectedLogMessage =
        String.format(
            "Skipping evaluation due to invalid year format %s.",
            invalidYear, HARDCODED_PUBLICATION_ID);
    assertTrue(logAppender.getMessages().contains(expectedLogMessage));
    assertEquals(0, queueClient.getSentMessages().size());
  }

  @Test
  void shouldEvaluateExistingCandidateInOpenPeriod() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var year = LocalDateTime.now().getYear();
    var resourceFileUri = setupCandidate(year);
    periodRepository = PeriodRepositoryFixtures.periodRepositoryReturningOpenedPeriod(year);
    setupEvaluatorService(periodRepository);
    handler = new EvaluateNviCandidateHandler(evaluatorService, queueClient, ENVIRONMENT);
    var event = createEvent(new PersistedResourceMessage(resourceFileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (NviCandidate) getMessageBody().candidate();
    assertThat(candidate.publicationBucketUri(), is(equalTo(resourceFileUri)));
  }

  @Test
  void shouldEvaluateStrippedCandidate() throws IOException {
    when(authorizedBackendUriRetriever.fetchResponse(any(), any()))
        .thenReturn(Optional.of(okResponse));
    mockOrganizationResponseForAffiliation(SIKT_CRISTIN_ORG_ID, null, uriRetriever);
    var path = "evaluator/candidate_stripped.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (NviCandidate) getMessageBody().candidate();
    assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
  }

  @Test
  void shouldCreateNewCandidateWithPointsOnlyForNviInstitutions() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    mockCristinResponseAndCustomerApiResponseForNonNviInstitution();
    var content =
        IoUtils.inputStreamFromResources(
            "evaluator/candidate_verifiedCreator_with_nonNviInstitution.json");
    var fileUri =
        s3Driver.insertFile(
            UnixPath.of("evaluator/candidate_verifiedCreator_with_nonNviInstitution.json"),
            content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var candidate = (NviCandidate) messageBody.candidate();
    assertEquals(1, candidate.institutionPoints().size());
    assertNotNull(getPointsForInstitution(candidate, CRISTIN_NVI_ORG_TOP_LEVEL_ID));
  }

  @Test
  void shouldCreateNewCandidateEventOnValidAcademicArticle() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var expectedPoints = BigDecimal.valueOf(1).setScale(SCALE, ROUNDING_MODE);
    var expectedEvaluatedMessage =
        getExpectedEvaluatedMessage(
            InstanceType.ACADEMIC_ARTICLE, expectedPoints, fileUri, JOURNAL, ONE, expectedPoints);
    assertEquals(expectedEvaluatedMessage, messageBody);
  }

  @Test
  void shouldCreateNewCandidateEventWithCorrectDataOnValidAcademicChapter() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var content = IoUtils.inputStreamFromResources(ACADEMIC_CHAPTER_PATH);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_CHAPTER_PATH), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var expectedPoints = BigDecimal.valueOf(1).setScale(SCALE, ROUNDING_MODE);
    var expectedEvaluatedMessage =
        getExpectedEvaluatedMessage(
            InstanceType.ACADEMIC_CHAPTER, expectedPoints, fileUri, SERIES, ONE, expectedPoints);
    assertEquals(expectedEvaluatedMessage, messageBody);
  }

  @Test
  void shouldCreateNewCandidateEventWithCorrectDataOnValidAcademicMonograph() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var content = IoUtils.inputStreamFromResources(ACADEMIC_MONOGRAPH_JSON_PATH);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_MONOGRAPH_JSON_PATH), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var expectedPoints = BigDecimal.valueOf(5).setScale(SCALE, ROUNDING_MODE);
    var expectedEvaluatedMessage =
        getExpectedEvaluatedMessage(
            ACADEMIC_MONOGRAPH,
            expectedPoints,
            fileUri,
            SERIES,
            BigDecimal.valueOf(5),
            expectedPoints);
    assertEquals(expectedEvaluatedMessage, messageBody);
  }

  @Test
  void shouldCreateNewCandidateEventWithCorrectDataOnValidAcademicCommentary() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var content = IoUtils.inputStreamFromResources(ACADEMIC_COMMENTARY_JSON_PATH);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_COMMENTARY_JSON_PATH), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var expectedPoints = BigDecimal.valueOf(5).setScale(SCALE, ROUNDING_MODE);
    var expectedEvaluatedMessage =
        getExpectedEvaluatedMessage(
            ACADEMIC_COMMENTARY,
            expectedPoints,
            fileUri,
            SERIES,
            BigDecimal.valueOf(5),
            expectedPoints);
    assertEquals(expectedEvaluatedMessage, messageBody);
  }

  @Test
  void shouldCreateNewCandidateEventWithCorrectDataOnValidAcademicLiteratureReview()
      throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var content = IoUtils.inputStreamFromResources(ACADEMIC_LITERATURE_REVIEW_JSON_PATH);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_LITERATURE_REVIEW_JSON_PATH), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var expectedPoints = BigDecimal.valueOf(1).setScale(SCALE, ROUNDING_MODE);
    var expectedEvaluatedMessage =
        getExpectedEvaluatedMessage(
            ACADEMIC_LITERATURE_REVIEW, expectedPoints, fileUri, JOURNAL, ONE, expectedPoints);
    assertEquals(expectedEvaluatedMessage, messageBody);
  }

  @Test
  void shouldCalculatePointsOnValidAcademicArticle() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var candidate = (NviCandidate) messageBody.candidate();
    assertThat(candidate.institutionPoints(), notNullValue());
    assertThat(
        getPointsForInstitution(candidate, CRISTIN_NVI_ORG_TOP_LEVEL_ID),
        is(equalTo(BigDecimal.valueOf(1).setScale(4, RoundingMode.HALF_UP))));
  }

  @Test
  void shouldCreateInstitutionApprovalsForTopLevelInstitutions() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var candidate = (NviCandidate) messageBody.candidate();
    assertThat(candidate.institutionPoints(), notNullValue());
    assertThat(getPointsForInstitution(candidate, CRISTIN_NVI_ORG_TOP_LEVEL_ID), notNullValue());
  }

  @Test
  void
      shouldCreateNewCandidateEventOnValidAcademicChapterWithSeriesLevelUnassignedWithPublisherLevel()
          throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var path = "evaluator/candidate_academicChapter_seriesLevelUnassignedPublisherLevelOne.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (NviCandidate) getMessageBody().candidate();
    assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
  }

  @Test
  void
      shouldCreateNewCandidateEventOnValidAcademicMonographWithSeriesLevelUnassignedWithPublisherLevel()
          throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var path = "evaluator/candidate_academicMonograph_seriesLevelUnassignedPublisherLevelOne.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (NviCandidate) getMessageBody().candidate();
    assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
  }

  @Test
  void shouldCreateNewCandidateEventOnValidAcademicCommentaryWithoutSeriesLevelWithPublisherLevel()
      throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var path = "evaluator/candidate_academicCommentary_withoutSeries.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (NviCandidate) getMessageBody().candidate();
    assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
  }

  @Test
  void shouldCreateNewCandidateEventOnValidAcademicMonograph() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var content = IoUtils.inputStreamFromResources(ACADEMIC_MONOGRAPH_JSON_PATH);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_MONOGRAPH_JSON_PATH), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (NviCandidate) getMessageBody().candidate();
    assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
  }

  @Test
  void shouldCreateNewCandidateEventOnValidAcademicLiteratureReview() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var content = IoUtils.inputStreamFromResources(ACADEMIC_LITERATURE_REVIEW_JSON_PATH);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_LITERATURE_REVIEW_JSON_PATH), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (NviCandidate) getMessageBody().candidate();
    assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
  }

  @Test
  void shouldCreateNonCandidateEventOnAcademicChapterWithSeriesLevelZero() throws IOException {
    var path = "evaluator/nonCandidate_academicChapter_seriesLevelZero.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
    assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
  }

  @Test
  void shouldCreateNonCandidateEventOnAcademicCommentaryWithSeriesLevelZero() throws IOException {
    var path = "evaluator/nonCandidate_academicCommentary_seriesLevelZero.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
    assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
  }

  @Test
  void shouldCreateNonCandidateEventWhenIdentityIsNotVerified() throws IOException {
    var path = "evaluator/nonCandidate_nonVerified.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
    assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
  }

  @Test
  void shouldCreateNonCandidateEventWhenPublicationIsNotPublished() throws IOException {
    var path = "evaluator/nonCandidate_notPublished.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
    assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
  }

  @Test
  void shouldCreateNonCandidateForMusicalArts() throws IOException {
    var path = "evaluator/nonCandidate_musicalArts.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
    assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
  }

  @Test
  void shouldCreateNonCandidateIfSeriesInMonographHasNviLevelZero() throws IOException {
    var path = "evaluator/nonCandidate_notValidMonographArticle.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
    assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
  }

  @Test
  void shouldThrowExceptionIfFileDoesntExist() {
    var event =
        createEvent(new PersistedResourceMessage(UriWrapper.fromUri("s3://dummy").getUri()));
    assertThrows(RuntimeException.class, () -> handler.handleRequest(event, CONTEXT));
  }

  @Test
  void shouldCreateNonCandidateEventWhenZeroNviInstitutions() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(notFoundResponse);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
    assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
  }

  @Test
  void shouldThrowExceptionWhenProblemsFetchingCristinOrganization() throws IOException {
    when(uriRetriever.fetchResponse(any(), any()))
        .thenReturn(Optional.of(internalServerErrorResponse));
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    var appender = LogUtils.getTestingAppenderForRootLogger();
    assertThrows(RuntimeException.class, () -> handler.handleRequest(event, CONTEXT));
    assertThat(appender.getMessages(), containsString(ERROR_COULD_NOT_FETCH_CRISTIN_ORG));
  }

  @Test
  void shouldThrowExceptionWhenProblemsFetchingCustomer() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(internalServerErrorResponse);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    var appender = LogUtils.getTestingAppenderForRootLogger();
    assertThrows(RuntimeException.class, () -> handler.handleRequest(event, CONTEXT));
    assertThat(appender.getMessages(), containsString("status code: 500"));
  }

  @Test
  void shouldCreateNewCandidateEventWhenAffiliationAreNviInstitutions() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (NviCandidate) getMessageBody().candidate();
    assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
  }

  @Test
  @Deprecated
  void shouldHandleSeriesWithMultipleTypes() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var path = "evaluator/candidate_academicMonograph_series_multiple_types.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var expectedPoints = BigDecimal.valueOf(5).setScale(SCALE, ROUNDING_MODE);
    var expectedEvaluatedMessage =
        getExpectedEvaluatedMessage(
            ACADEMIC_MONOGRAPH,
            expectedPoints,
            fileUri,
            SERIES,
            BigDecimal.valueOf(5),
            expectedPoints);
    assertEquals(expectedEvaluatedMessage, messageBody);
  }

  @Test
  @Deprecated
  void shouldHandleJournalWithMultipleTypes() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var path = "evaluator/candidate_academicArticle_journal_multiple_types.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var expectedPoints = ONE.setScale(SCALE, ROUNDING_MODE);
    var expectedEvaluatedMessage =
        getExpectedEvaluatedMessage(
            InstanceType.ACADEMIC_ARTICLE,
            expectedPoints,
            fileUri,
            JOURNAL,
            BigDecimal.valueOf(1),
            expectedPoints);
    assertEquals(expectedEvaluatedMessage, messageBody);
  }

  private static CandidateEvaluatedMessage getExpectedEvaluatedMessage(
      InstanceType instanceType,
      BigDecimal points,
      URI bucketUri,
      PublicationChannel publicationChannel,
      BigDecimal basePoints,
      BigDecimal totalPoints) {
    return CandidateEvaluatedMessage.builder()
        .withCandidateType(
            createExpectedCandidate(
                instanceType,
                Map.of(CRISTIN_NVI_ORG_TOP_LEVEL_ID, points.setScale(SCALE, RoundingMode.HALF_UP)),
                publicationChannel,
                ScientificValue.LEVEL_ONE.getValue(),
                basePoints,
                totalPoints,
                bucketUri))
        .build();
  }

  private static NviCandidate createExpectedCandidate(
      InstanceType instanceType,
      Map<URI, BigDecimal> institutionPoints,
      PublicationChannel channelType,
      String level,
      BigDecimal basePoints,
      BigDecimal totalPoints,
      URI publicationBucketUri) {
    var verifiedCreators =
        List.of(
            new VerifiedNviCreatorDto(HARDCODED_CREATOR_ID, List.of(CRISTIN_NVI_ORG_SUB_UNIT_ID)));
    return NviCandidate.builder()
        .withPublicationId(HARDCODED_PUBLICATION_ID)
        .withPublicationBucketUri(publicationBucketUri)
        .withDate(HARDCODED_PUBLICATION_DATE)
        .withInstanceType(instanceType)
        .withChannelType(channelType.getValue())
        .withLevel(level)
        .withPublicationChannelId(HARDCODED_PUBLICATION_CHANNEL_ID)
        .withIsInternationalCollaboration(false)
        .withCollaborationFactor(ONE.setScale(1, ROUNDING_MODE))
        .withCreatorShareCount(countCreatorShares(verifiedCreators))
        .withBasePoints(basePoints)
        .withVerifiedNviCreators(verifiedCreators)
        .withInstitutionPoints(
            institutionPoints.entrySet().stream()
                .map(
                    entry ->
                        new InstitutionPoints(
                            entry.getKey(),
                            entry.getValue(),
                            List.of(
                                new CreatorAffiliationPoints(
                                    HARDCODED_CREATOR_ID,
                                    CRISTIN_NVI_ORG_SUB_UNIT_ID,
                                    entry.getValue()))))
                .toList())
        .withTotalPoints(totalPoints)
        .build();
  }

  private static int countCreatorShares(List<VerifiedNviCreatorDto> nviCreators) {
    return (int) nviCreators.stream().mapToLong(creator -> creator.affiliations().size()).sum();
  }

  private URI setupPublicationWithInvalidYear(String year) throws IOException {
    var path = "evaluator/candidate_publicationDate_replace_year.json";
    var content = IoUtils.stringFromResources(Path.of(path)).replace("__REPLACE_YEAR__", year);
    return s3Driver.insertFile(UnixPath.of(path), content);
  }

  private void setupEvaluatorService(PeriodRepository periodRepository) {
    var calculator = new CreatorVerificationUtil(authorizedBackendUriRetriever, uriRetriever);
    var organizationRetriever = new OrganizationRetriever(uriRetriever);
    var pointCalculator = new PointService(organizationRetriever);
    evaluatorService =
        new EvaluatorService(
            storageReader, calculator, pointCalculator, candidateRepository, periodRepository);
  }

  private URI setupCandidate(int year) throws IOException {
    var upsertCandidateRequest =
        randomUpsertRequestBuilder()
            .withPublicationDate(
                new PublicationDetails.PublicationDate(String.valueOf(year), null, null))
            .build();
    Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository);
    var candidateInClosedPeriod =
        Candidate.fetchByPublicationId(
            upsertCandidateRequest::publicationId, candidateRepository, periodRepository);
    var content =
        IoUtils.stringFromResources(Path.of(ACADEMIC_ARTICLE_PATH))
            .replace(
                "__REPLACE_WITH_PUBLICATION_ID__",
                candidateInClosedPeriod.getPublicationId().toString());
    return s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), content);
  }

  private CandidateEvaluatedMessage getMessageBody() {
    var sentMessages = queueClient.getSentMessages();
    assertThat(sentMessages, hasSize(1));
    var message = sentMessages.getFirst();
    return attempt(
            () -> objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class))
        .orElseThrow();
  }

  private HttpResponse<String> getNonNviCustomerResponseBody() {
    var body = IoUtils.stringFromResources(Path.of(NON_NVI_CUSTOMER_PATH));
    return createResponse(200, body);
  }

  private void mockCristinResponseAndCustomerApiResponseForNonNviInstitution() {
    var cristinOrgNonNviSubUnit =
        URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/150.50.50.0");
    var cristinOrgNonNviTopLevel =
        URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/150.0.0.0");
    var customerApiEndpoint = URI.create("https://api.dev.nva.aws.unit.no/customer/cristinId");
    var cristinOrgNonNviTopLevelCustomerApiUri =
        URI.create(
            customerApiEndpoint
                + "/"
                + URLEncoder.encode(cristinOrgNonNviTopLevel.toString(), StandardCharsets.UTF_8));
    var expectedResponse = getNonNviCustomerResponseBody();
    when(authorizedBackendUriRetriever.fetchResponse(
            eq(cristinOrgNonNviTopLevelCustomerApiUri), any()))
        .thenReturn(Optional.of(expectedResponse));
    mockOrganizationResponseForAffiliation(
        cristinOrgNonNviTopLevel, cristinOrgNonNviSubUnit, uriRetriever);
  }

  private void mockCristinResponseAndCustomerApiResponseForNviInstitution(
      HttpResponse<String> httpResponse) {
    mockOrganizationResponseForAffiliation(
        CRISTIN_NVI_ORG_TOP_LEVEL_ID, CRISTIN_NVI_ORG_SUB_UNIT_ID, uriRetriever);
    when(authorizedBackendUriRetriever.fetchResponse(
            eq(CUSTOMER_API_CRISTIN_NVI_ORG_TOP_LEVEL), any()))
        .thenReturn(Optional.of(httpResponse));
  }

  @Nested
  @DisplayName("Test cases with dynamic test data")
  class evaluateNviCandidatesWithDynamicTestData {

    // Builders and variables that may be modified for each test case
    SampleExpandedContributor.Builder defaultVerifiedContributor;
    SampleExpandedContributor.Builder defaultUnverifiedContributor;
    List<SampleExpandedContributor.Builder> verifiedContributors;
    List<SampleExpandedContributor.Builder> unverifiedContributors;
    int creatorShareCount;

    URI publicationChannelId;
    String publicationChannelType;
    String publicationChannelLevel;
    List<SampleExpandedPublicationChannel.Builder> publicationChannels;

    String publicationInstanceType;
    SampleExpandedPublication.Builder publicationBuilder;

    BigDecimal expectedTotalPoints;
    List<InstitutionPoints> expectedPointsPerInstitution;
    NviCandidate.Builder expectedCandidateBuilder;

    @BeforeEach
    void setup() {
      // Initialize default values for all test data
      defaultVerifiedContributor =
          SampleExpandedContributor.builder()
              .withVerificationStatus("Verified")
              .withAffiliations(List.of(DEFAULT_SUBUNIT_AFFILIATION));
      defaultUnverifiedContributor =
          SampleExpandedContributor.builder()
              .withId(null)
              .withVerificationStatus(null)
              .withAffiliations(List.of(DEFAULT_SUBUNIT_AFFILIATION));
      verifiedContributors = List.of(defaultVerifiedContributor);
      unverifiedContributors = emptyList();
      creatorShareCount = 1;

      publicationChannelId = HARDCODED_PUBLICATION_CHANNEL_ID;
      publicationChannelType = JOURNAL.getValue();
      publicationChannelLevel = ScientificValue.LEVEL_ONE.getValue();
      publicationInstanceType = InstanceType.ACADEMIC_ARTICLE.getValue();
      publicationChannels = List.of(getDefaultPublicationChannelBuilder());

      publicationBuilder =
          SampleExpandedPublication.builder().withPublicationDate(HARDCODED_JSON_PUBLICATION_DATE);

      expectedTotalPoints = ONE.setScale(SCALE, ROUNDING_MODE);
      expectedPointsPerInstitution =
          List.of(
              new InstitutionPoints(
                  CRISTIN_NVI_ORG_TOP_LEVEL_ID,
                  expectedTotalPoints,
                  List.of(
                      new CreatorAffiliationPoints(
                          defaultVerifiedContributor.build().id(),
                          CRISTIN_NVI_ORG_SUB_UNIT_ID,
                          expectedTotalPoints))));

      expectedCandidateBuilder =
          NviCandidate.builder()
              .withIsInternationalCollaboration(false)
              .withCollaborationFactor(ONE.setScale(1, ROUNDING_MODE))
              .withBasePoints(ONE);
    }

    @Test
    void shouldIdentifyCandidateWithOnlyVerifiedNviCreators() throws IOException {
      var testScenario = getCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldIdentifyCandidateWithOnlyUnverifiedNviCreators() throws IOException {
      verifiedContributors = emptyList();
      unverifiedContributors = List.of(defaultUnverifiedContributor);
      expectedTotalPoints = ZERO.setScale(SCALE, ROUNDING_MODE);
      expectedPointsPerInstitution =
          List.of(
              new InstitutionPoints(
                  CRISTIN_NVI_ORG_TOP_LEVEL_ID, expectedTotalPoints, emptyList()));
      var testScenario = getCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldIdentifyCandidateWithUnnamedVerifiedAuthor() throws IOException {
      var verifiedContributor = defaultVerifiedContributor.withName(null);
      verifiedContributors = List.of(verifiedContributor);
      var testScenario = getCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldRejectUnverifiedAuthorsWithoutName() throws IOException {
      var unnamedContributor = defaultUnverifiedContributor.withName(null);
      verifiedContributors = emptyList();
      unverifiedContributors = List.of(unnamedContributor);
      var testScenario = getNonCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldIdentifyCandidateWithBothVerifiedAndUnverifiedNviCreators() throws IOException {
      unverifiedContributors = List.of(defaultUnverifiedContributor);
      expectedTotalPoints = BigDecimal.valueOf(0.7071).setScale(SCALE, ROUNDING_MODE);
      expectedPointsPerInstitution =
          List.of(
              new InstitutionPoints(
                  CRISTIN_NVI_ORG_TOP_LEVEL_ID,
                  expectedTotalPoints,
                  List.of(
                      new CreatorAffiliationPoints(
                          defaultVerifiedContributor.build().id(),
                          CRISTIN_NVI_ORG_SUB_UNIT_ID,
                          expectedTotalPoints))));
      creatorShareCount = 2;
      var testScenario = getCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldIdentifyCandidateWithMissingCountryCode() throws IOException {
      var affiliations =
          List.of(
              SampleExpandedAffiliation.builder()
                  .withId(CRISTIN_NVI_ORG_SUB_UNIT_ID)
                  .withCountryCode(null)
                  .build());
      var contributor = defaultVerifiedContributor.withAffiliations(affiliations);
      verifiedContributors = List.of(contributor);
      var testScenario = getCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldRejectCandidateWithOnlySwedishCountryCode() throws IOException {
      var affiliations =
          List.of(SampleExpandedAffiliation.builder().withCountryCode(COUNTRY_CODE_SWEDEN).build());
      var swedishContributor = defaultVerifiedContributor.withAffiliations(affiliations);
      verifiedContributors = List.of(swedishContributor);
      var testScenario = getNonCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldEvaluateCandidateInOpenPeriod() throws IOException {
      // Given a publication that fulfills all criteria for NVI reporting
      // And the publication is published in an open period
      // When the publication is evaluated
      // Then it should be evaluated as a Candidate
      var testScenario = getCandidateScenario();
      var year = HARDCODED_JSON_PUBLICATION_DATE.year();
      setupOpenPeriod(scenario, year);

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldEvaluateExistingCandidateInClosedPeriod() throws IOException {
      // Given a publication that has been evaluated as an applicable Candidate
      // And the publication is published in a closed period
      // When the publication is evaluated
      // Then it should be evaluated as a Candidate
      var year = HARDCODED_JSON_PUBLICATION_DATE.year();
      setupClosedPeriod(scenario, year);
      setupCandidateMatchingPublication(buildExpectedPublication());
      var testScenario = getCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldEvaluateExistingCandidateInClosedPeriodThatIsNoLongerApplicable()
        throws IOException {
      // Given a publication that has been evaluated as an applicable Candidate
      // And the publication is published in a closed period
      // When the publication is updated to be no longer applicable
      // Then it should be re-evaluated as a NonCandidate
      var year = HARDCODED_JSON_PUBLICATION_DATE.year();
      setupClosedPeriod(scenario, year);
      setupCandidateMatchingPublication(buildExpectedPublication());
      publicationInstanceType = "ComicBook";
      var testScenario = getNonCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldEvaluateNewPublicationAsNonCandidateInClosedPeriod() throws IOException {
      // Given a publication that fulfills all criteria for NVI reporting except for the publication
      // year
      // And the publication is published in a closed period
      // And the publication is not already a Candidate
      // When the publication is evaluated
      // Then it should be evaluated as a NonCandidate
      var year = HARDCODED_JSON_PUBLICATION_DATE.year();
      setupClosedPeriod(scenario, year);
      var testScenario = getNonCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldEvaluatePublicationAsNonCandidateIfPeriodDoesNotExist() throws IOException {
      // Given a publication that fulfills all criteria for NVI reporting except for the publication
      // year
      // And the publication is published before the first registered NVI period
      // When the publication is evaluated
      // Then it should be evaluated as a NonCandidate
      var year = "2000";
      var publicationDate = new SampleExpandedPublicationDate(year, null, null);
      publicationBuilder = publicationBuilder.withPublicationDate(publicationDate);
      var testScenario = getNonCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();
      var nviPeriod = periodRepository.findByPublishingYear(year);

      assertTrue(nviPeriod.isEmpty());
      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldNotEvaluateReportedCandidate() throws IOException {
      // Given a publication that fulfills all criteria for NVI reporting
      // And the publication is already a Candidate
      // And the publication is already Reported
      // When the publication is evaluated
      // Then the evaluation should be skipped
      // And the Candidate entry in the database should not be updated
      var year = HARDCODED_JSON_PUBLICATION_DATE.year();
      var existingCandidateDao = setupReportedCandidate(candidateRepository, year);
      publicationBuilder =
          publicationBuilder.withId(existingCandidateDao.candidate().publicationId());
      var testScenario = getCandidateScenario();

      handler.handleRequest(testScenario.event(), CONTEXT);

      assertEquals(0, queueClient.getSentMessages().size());
    }

    @Test
    void shouldReEvaluatePublicationMovedToFuturePeriod() throws IOException {
      // Given a publication that is an applicable Candidate
      // And the publication is published in an open period
      // When the publication date is updated to a year with no registered NVI period
      // Then the publication should be re-evaluated as a NonCandidate
      var openPeriod = CURRENT_YEAR;
      var nonPeriod = CURRENT_YEAR + 10;
      setupOpenPeriod(scenario, openPeriod);
      var originalDate = new SampleExpandedPublicationDate(String.valueOf(openPeriod), null, null);
      var newDate = new SampleExpandedPublicationDate(String.valueOf(nonPeriod), null, null);

      publicationBuilder = publicationBuilder.withPublicationDate(originalDate);
      setupCandidateMatchingPublication(buildExpectedPublication());

      publicationBuilder = publicationBuilder.withPublicationDate(newDate);
      setupCandidateMatchingPublication(buildExpectedPublication());

      var testScenario = getNonCandidateScenario();
      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    @Test
    void shouldReEvaluatePublicationMovedFromFuturePeriodToOpenPeriod() throws IOException {
      // Given a publication that is an applicable Candidate
      // And the publication is published in an open period
      // When the publication date is updated to a year with no registered NVI period
      // And the publication date is updated to a year with an open NVI period
      // Then the publication should be re-evaluated as a Candidate
      var openPeriod = CURRENT_YEAR;
      var nonPeriod = CURRENT_YEAR + 10;
      setupOpenPeriod(scenario, openPeriod);
      var originalDate = new SampleExpandedPublicationDate(String.valueOf(openPeriod), null, null);
      var newDate = new SampleExpandedPublicationDate(String.valueOf(nonPeriod), null, null);

      publicationBuilder = publicationBuilder.withPublicationDate(originalDate);
      setupCandidateMatchingPublication(buildExpectedPublication());

      publicationBuilder = publicationBuilder.withPublicationDate(newDate);
      setupCandidateMatchingPublication(buildExpectedPublication());

      publicationBuilder = publicationBuilder.withPublicationDate(originalDate);

      var testScenario = getCandidateScenario();
      handler.handleRequest(testScenario.event(), CONTEXT);
      var messageBody = getMessageBody();

      assertEquals(testScenario.expectedEvaluatedMessage(), messageBody);
    }

    private static PublicationDate getPublicationDate(
        SampleExpandedPublicationDate publicationDate) {
      return new PublicationDate(
          publicationDate.day(), publicationDate.month(), publicationDate.year());
    }

    private TestScenario getCandidateScenario() throws IOException {
      var publication = buildExpectedPublication();
      var fileUri = addPublicationToS3(publication);
      var expectedEvaluatedMessage = getCandidateResponse(fileUri, publication);
      var event = createEvent(new PersistedResourceMessage(fileUri));
      mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
      return new TestScenario(publication, expectedEvaluatedMessage, event);
    }

    private TestScenario getNonCandidateScenario() throws IOException {
      var publication = buildExpectedPublication();
      var fileUri = addPublicationToS3(publication);
      var expectedEvaluatedMessage = getNonCandidateResponse(publication);
      var event = createEvent(new PersistedResourceMessage(fileUri));
      mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
      return new TestScenario(publication, expectedEvaluatedMessage, event);
    }

    private SampleExpandedPublication buildExpectedPublication() {
      // Generate test data based on the current state of the builders,
      // after the test case has made any necessary changes to the default values.
      var allContributors =
          Stream.concat(verifiedContributors.stream(), unverifiedContributors.stream())
              .map(SampleExpandedContributor.Builder::build)
              .toList();
      return publicationBuilder
          .withInstanceType(publicationInstanceType)
          .withPublicationChannels(
              publicationChannels.stream()
                  .map(SampleExpandedPublicationChannel.Builder::build)
                  .toList())
          .withContributors(allContributors)
          .build();
    }

    private SampleExpandedPublicationChannel.Builder getDefaultPublicationChannelBuilder() {
      return SampleExpandedPublicationChannel.builder()
          .withId(publicationChannelId)
          .withType(publicationChannelType)
          .withLevel(publicationChannelLevel);
    }

    private CandidateEvaluatedMessage getCandidateResponse(
        URI fileUri, SampleExpandedPublication publication) {
      var publicationDate = getPublicationDate(publication.publicationDate());
      var verifiedCreators = getVerifiedNviCreators();
      var unverifiedNviCreators = getUnverifiedNviCreators();
      var expectedCandidate =
          expectedCandidateBuilder
              .withPublicationId(publication.id())
              .withPublicationBucketUri(fileUri)
              .withDate(publicationDate)
              .withInstanceType(InstanceType.parse(publication.instanceType()))
              .withChannelType(publicationChannelType)
              .withPublicationChannelId(publicationChannelId)
              .withLevel(publicationChannelLevel)
              .withInstitutionPoints(expectedPointsPerInstitution)
              .withTotalPoints(expectedTotalPoints)
              .withVerifiedNviCreators(verifiedCreators)
              .withUnverifiedNviCreators(unverifiedNviCreators)
              .withCreatorShareCount(creatorShareCount)
              .build();
      return CandidateEvaluatedMessage.builder().withCandidateType(expectedCandidate).build();
    }

    private CandidateEvaluatedMessage getNonCandidateResponse(
        SampleExpandedPublication publication) {
      var rejectedCandidate = new NonNviCandidate(publication.id());
      return CandidateEvaluatedMessage.builder().withCandidateType(rejectedCandidate).build();
    }

    private List<VerifiedNviCreatorDto> getVerifiedNviCreators() {
      return verifiedContributors.stream()
          .map(SampleExpandedContributor.Builder::build)
          .map(
              contributor ->
                  new VerifiedNviCreatorDto(contributor.id(), contributor.affiliationIds()))
          .toList();
    }

    private List<UnverifiedNviCreatorDto> getUnverifiedNviCreators() {
      return unverifiedContributors.stream()
          .map(SampleExpandedContributor.Builder::build)
          .map(
              contributor ->
                  new UnverifiedNviCreatorDto(
                      contributor.contributorName(), contributor.affiliationIds()))
          .toList();
    }

    private URI addPublicationToS3(SampleExpandedPublication publication) throws IOException {

      return s3Driver.insertFile(
          UnixPath.of(publication.identifier().toString()), publication.toJsonString());
    }

    private record TestScenario(
        SampleExpandedPublication publication,
        CandidateEvaluatedMessage expectedEvaluatedMessage,
        SQSEvent event) {}

    private void setupCandidateMatchingPublication(
        SampleExpandedPublication sampleExpandedPublication) {
      var year = sampleExpandedPublication.publicationDate().year();
      var upsertCandidateRequest =
          randomUpsertRequestBuilder()
              .withPublicationDate(new PublicationDetails.PublicationDate(year, null, null))
              .withPublicationId(sampleExpandedPublication.id())
              .build();
      Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository);
    }
  }
}
