package no.sikt.nva.nvi.events.evaluator;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getEvaluateNviCandidateHandlerEnvironment;
import static no.sikt.nva.nvi.common.SampleExpandedPublicationFactory.mapOrganizationToAffiliation;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.setupReportedCandidate;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.ChannelType.JOURNAL;
import static no.sikt.nva.nvi.common.model.ChannelType.SERIES;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.ROLE_CREATOR;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.STATUS_UNVERIFIED;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.randomContributorDtoBuilder;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.unverifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.verifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_COMMENTARY;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_LITERATURE_REVIEW;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_MONOGRAPH;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDate;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateInYear;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static no.sikt.nva.nvi.test.TestConstants.CRISTIN_NVI_ORG_SUB_UNIT_ID;
import static no.sikt.nva.nvi.test.TestConstants.CRISTIN_NVI_ORG_TOP_LEVEL_ID;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_CREATOR_ID;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_PUBLICATION_ID;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createResponse;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
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
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PointCalculationDto;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.dto.PublicationDetailsDto;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.model.PublicationChannel;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.test.SampleExpandedContributor;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings("PMD.CouplingBetweenObjects")
class EvaluateNviCandidateHandlerTest extends EvaluationTest {

  public static final PublicationDateDto HARDCODED_PUBLICATION_DATE =
      new PublicationDateDto("2023", null, null);
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
      stringFromResources(Path.of(ACADEMIC_ARTICLE_PATH))
          .replace("__REPLACE_WITH_PUBLICATION_ID__", HARDCODED_PUBLICATION_ID.toString());
  private static final URI CUSTOMER_API_CRISTIN_NVI_ORG_TOP_LEVEL =
      URI.create(
          "https://api.fake.nva.aws.unit.no/customer/cristinId/https%3A%2F%2Fapi"
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
    var candidate = (UpsertNviCandidateRequest) getMessageBody().candidate();
    assertEquals(candidate.publicationBucketUri(), fileUri);
  }

  @ParameterizedTest
  @MethodSource("invalidPublicationProvider")
  void shouldSkipEvaluationAndLogWarningOnPublicationWithInvalidYear(String content)
      throws IOException {
    var fileUri = s3Driver.insertFile(UnixPath.of(randomString()), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    final var logAppender = LogUtils.getTestingAppender(EvaluatorService.class);
    handler.handleRequest(event, CONTEXT);
    var expectedLogMessage = "Skipping evaluation due to invalid year format";
    assertTrue(logAppender.getMessages().contains(expectedLogMessage));
    assertEquals(0, queueClient.getSentMessages().size());
  }

  private static Stream<Arguments> invalidPublicationProvider() {
    var documentWithMalformedDate =
        stringFromResources(Path.of("evaluator/candidate_publicationDate_replace_year.json"))
            .replace("__REPLACE_YEAR__", "1948-1997");
    var documentWithMissingDate =
        stringFromResources(Path.of("expandedPublications/invalidDraft.json"));
    return Stream.of(
        argumentSet("Malformed publication year", documentWithMalformedDate),
        argumentSet("Missing publication date", documentWithMissingDate));
  }

  @Test
  void shouldEvaluateExistingCandidateInOpenPeriod() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var year = LocalDateTime.now().getYear();
    setupOpenPeriod(scenario, year);
    var resourceFileUri = setupCandidate(year);
    setupEvaluatorService();

    var event = createEvent(new PersistedResourceMessage(resourceFileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (UpsertNviCandidateRequest) getMessageBody().candidate();
    assertEquals(candidate.publicationBucketUri(), resourceFileUri);
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
    var candidate = (UpsertNviCandidateRequest) getMessageBody().candidate();
    assertEquals(candidate.publicationBucketUri(), fileUri);
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
    var candidate = (UpsertNviCandidateRequest) messageBody.candidate();
    assertEquals(1, candidate.pointCalculation().institutionPoints().size());
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
    assertThatEvaluatedMessageEqualsExpectedMessage(expectedEvaluatedMessage, messageBody);
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
    assertThatEvaluatedMessageEqualsExpectedMessage(expectedEvaluatedMessage, messageBody);
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
    assertThatEvaluatedMessageEqualsExpectedMessage(expectedEvaluatedMessage, messageBody);
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
    assertThatEvaluatedMessageEqualsExpectedMessage(expectedEvaluatedMessage, messageBody);
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
    assertThatEvaluatedMessageEqualsExpectedMessage(expectedEvaluatedMessage, messageBody);
  }

  @Test
  void shouldCalculatePointsOnValidAcademicArticle() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var candidate = (UpsertNviCandidateRequest) messageBody.candidate();
    assertNotNull(candidate.pointCalculation().institutionPoints());
    assertEquals(
        getPointsForInstitution(candidate, CRISTIN_NVI_ORG_TOP_LEVEL_ID),
        BigDecimal.valueOf(1).setScale(4, RoundingMode.HALF_UP));
  }

  @Test
  void shouldCreateInstitutionApprovalsForTopLevelInstitutions() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var messageBody = getMessageBody();
    var candidate = (UpsertNviCandidateRequest) messageBody.candidate();
    assertNotNull(candidate.pointCalculation().institutionPoints());
    assertNotNull(getPointsForInstitution(candidate, CRISTIN_NVI_ORG_TOP_LEVEL_ID));
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
    var candidate = (UpsertNviCandidateRequest) getMessageBody().candidate();
    assertEquals(candidate.publicationBucketUri(), fileUri);
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
    var candidate = (UpsertNviCandidateRequest) getMessageBody().candidate();
    assertEquals(candidate.publicationBucketUri(), fileUri);
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
    var candidate = (UpsertNviCandidateRequest) getMessageBody().candidate();
    assertEquals(candidate.publicationBucketUri(), fileUri);
  }

  @Test
  void shouldCreateNewCandidateEventOnValidAcademicMonograph() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var content = IoUtils.inputStreamFromResources(ACADEMIC_MONOGRAPH_JSON_PATH);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_MONOGRAPH_JSON_PATH), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (UpsertNviCandidateRequest) getMessageBody().candidate();
    assertEquals(candidate.publicationBucketUri(), fileUri);
  }

  @Test
  void shouldCreateNewCandidateEventOnValidAcademicLiteratureReview() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var content = IoUtils.inputStreamFromResources(ACADEMIC_LITERATURE_REVIEW_JSON_PATH);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_LITERATURE_REVIEW_JSON_PATH), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (UpsertNviCandidateRequest) getMessageBody().candidate();
    assertEquals(candidate.publicationBucketUri(), fileUri);
  }

  @Test
  void shouldCreateNonCandidateEventOnAcademicChapterWithSeriesLevelZero() throws IOException {
    var path = "evaluator/nonCandidate_academicChapter_seriesLevelZero.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (UpsertNonNviCandidateRequest) getMessageBody().candidate();
    assertEquals(HARDCODED_PUBLICATION_ID, nonCandidate.publicationId());
  }

  @Test
  void shouldCreateNonCandidateEventOnAcademicCommentaryWithSeriesLevelZero() throws IOException {
    var path = "evaluator/nonCandidate_academicCommentary_seriesLevelZero.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (UpsertNonNviCandidateRequest) getMessageBody().candidate();
    assertEquals(HARDCODED_PUBLICATION_ID, nonCandidate.publicationId());
  }

  @Test
  void shouldCreateNonCandidateEventWhenIdentityIsNotVerified() throws IOException {
    var path = "evaluator/nonCandidate_nonVerified.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (UpsertNonNviCandidateRequest) getMessageBody().candidate();
    assertEquals(HARDCODED_PUBLICATION_ID, nonCandidate.publicationId());
  }

  @Test
  void shouldCreateNonCandidateEventWhenPublicationIsNotPublished() throws IOException {
    var path = "evaluator/nonCandidate_notPublished.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (UpsertNonNviCandidateRequest) getMessageBody().candidate();
    assertEquals(HARDCODED_PUBLICATION_ID, nonCandidate.publicationId());
  }

  @Test
  void shouldCreateNonCandidateForMusicalArts() throws IOException {
    var path = "evaluator/nonCandidate_musicalArts.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (UpsertNonNviCandidateRequest) getMessageBody().candidate();
    assertEquals(HARDCODED_PUBLICATION_ID, nonCandidate.publicationId());
  }

  @Test
  void shouldCreateNonCandidateIfSeriesInMonographHasNviLevelZero() throws IOException {
    var path = "evaluator/nonCandidate_notValidMonographArticle.json";
    var content = IoUtils.inputStreamFromResources(path);
    var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var nonCandidate = (UpsertNonNviCandidateRequest) getMessageBody().candidate();
    assertEquals(HARDCODED_PUBLICATION_ID, nonCandidate.publicationId());
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
    var nonCandidate = (UpsertNonNviCandidateRequest) getMessageBody().candidate();
    assertEquals(HARDCODED_PUBLICATION_ID, nonCandidate.publicationId());
  }

  @Test
  void shouldThrowExceptionWhenProblemsFetchingCustomerOrganization() throws IOException {
    when(uriRetriever.fetchResponse(any(), any()))
        .thenReturn(Optional.of(internalServerErrorResponse));
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    assertThrows(RuntimeException.class, () -> handler.handleRequest(event, CONTEXT));
  }

  @Test
  void shouldThrowExceptionWhenProblemsFetchingCustomer() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(internalServerErrorResponse);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    var appender = LogUtils.getTestingAppenderForRootLogger();
    assertThrows(RuntimeException.class, () -> handler.handleRequest(event, CONTEXT));
    assertThat(appender.getMessages()).contains("status code: 500");
  }

  @Test
  void shouldCreateNewCandidateEventWhenAffiliationAreNviInstitutions() throws IOException {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    var candidate = (UpsertNviCandidateRequest) getMessageBody().candidate();
    assertEquals(candidate.publicationBucketUri(), fileUri);
  }

  @Test
  @Deprecated
  void shouldHandleSeriesWithMultipleTypes() {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var path = "evaluator/candidate_academicMonograph_series_multiple_types.json";
    var candidate =
        evaluatePublicationAndGetPersistedCandidate(
            HARDCODED_PUBLICATION_ID, stringFromResources(Path.of(path)));
    var expectedChannel =
        new PublicationChannel(HARDCODED_PUBLICATION_CHANNEL_ID, SERIES, ScientificValue.LEVEL_ONE);
    assertThat(candidate)
        .extracting(
            Candidate::getPublicationType,
            Candidate::getPublicationChannel,
            Candidate::isApplicable,
            Candidate::getTotalPoints)
        .containsExactly(
            ACADEMIC_MONOGRAPH,
            expectedChannel,
            true,
            BigDecimal.valueOf(5).setScale(SCALE, ROUNDING_MODE));
  }

  @Test
  @Deprecated
  void shouldHandleJournalWithMultipleTypes() {
    mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
    var path = "evaluator/candidate_academicArticle_journal_multiple_types.json";
    var candidate =
        evaluatePublicationAndGetPersistedCandidate(
            HARDCODED_PUBLICATION_ID, stringFromResources(Path.of(path)));
    var expectedChannel =
        new PublicationChannel(
            HARDCODED_PUBLICATION_CHANNEL_ID, JOURNAL, ScientificValue.LEVEL_ONE);
    assertThat(candidate)
        .extracting(
            Candidate::getPublicationType,
            Candidate::getPublicationChannel,
            Candidate::isApplicable,
            Candidate::getTotalPoints)
        .containsExactly(
            InstanceType.ACADEMIC_ARTICLE,
            expectedChannel,
            true,
            ONE.setScale(SCALE, ROUNDING_MODE));
  }

  private static CandidateEvaluatedMessage getExpectedEvaluatedMessage(
      InstanceType instanceType,
      BigDecimal points,
      URI bucketUri,
      ChannelType publicationChannel,
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

  private static UpsertNviCandidateRequest createExpectedCandidate(
      InstanceType instanceType,
      Map<URI, BigDecimal> institutionPoints,
      ChannelType channelType,
      String level,
      BigDecimal basePoints,
      BigDecimal totalPoints,
      URI publicationBucketUri) {
    var channelForLevel =
        PublicationChannelDto.builder()
            .withId(HARDCODED_PUBLICATION_CHANNEL_ID)
            .withChannelType(channelType)
            .withScientificValue(ScientificValue.parse(level))
            .build();
    var publicationDetails =
        createExpectedPublicationDetails(HARDCODED_PUBLICATION_ID, HARDCODED_PUBLICATION_DATE);
    var verifiedCreator =
        new VerifiedNviCreatorDto(HARDCODED_CREATOR_ID, null, List.of(CRISTIN_NVI_ORG_SUB_UNIT_ID));
    var expectedInstitutionPoints =
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
            .toList();
    var pointCalculation =
        new PointCalculationDto(
            instanceType,
            channelForLevel,
            false,
            ONE.setScale(1, ROUNDING_MODE),
            basePoints,
            countCreatorShares(List.of(verifiedCreator)),
            expectedInstitutionPoints,
            totalPoints);
    return UpsertNviCandidateRequest.builder()
        .withPublicationBucketUri(publicationBucketUri)
        .withPointCalculation(pointCalculation)
        .withPublicationDetails(publicationDetails)
        .withNviCreators(List.of(verifiedCreator))
        .build();
  }

  private static PublicationDetailsDto createExpectedPublicationDetails(
      URI publicationId, PublicationDateDto publicationDate) {
    return new PublicationDetailsDto(
        publicationId, null, null, "PUBLISHED", null, null, null, publicationDate, true, 1, null);
  }

  private static int countCreatorShares(List<VerifiedNviCreatorDto> nviCreators) {
    return nviCreators.stream().mapToInt(creator -> creator.affiliations().size()).sum();
  }

  private void setupEvaluatorService() {
    var environment = getEvaluateNviCandidateHandlerEnvironment();
    var calculator = new CreatorVerificationUtil(authorizedBackendUriRetriever, environment);
    evaluatorService =
        new EvaluatorService(
            scenario.getS3StorageReaderForExpandedResourcesBucket(), calculator, candidateService);
  }

  private URI setupCandidate(int year) throws IOException {
    var upsertCandidateRequest =
        randomUpsertRequestBuilder()
            .withPublicationDate(new PublicationDateDto(String.valueOf(year), null, null))
            .build();
    candidateService.upsert(upsertCandidateRequest);
    var candidateInClosedPeriod =
        candidateService.getByPublicationId(upsertCandidateRequest.publicationId());
    var content =
        stringFromResources(Path.of(ACADEMIC_ARTICLE_PATH))
            .replace(
                "__REPLACE_WITH_PUBLICATION_ID__",
                candidateInClosedPeriod.getPublicationId().toString());
    return s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), content);
  }

  private CandidateEvaluatedMessage getMessageBody() {
    var sentMessages = queueClient.getSentMessages();
    assertThat(sentMessages).hasSize(1);

    var message = sentMessages.getFirst();
    return attempt(
            () -> objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class))
        .orElseThrow();
  }

  private HttpResponse<String> getNonNviCustomerResponseBody() {
    var body = stringFromResources(Path.of(NON_NVI_CUSTOMER_PATH));
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

  /**
   * Asserts that the evaluated message equals the expected message, ignoring certain fields. These
   * fields are ignored because the static test data used here is outdated and the fields may be
   * missing, so we cannot easily construct an expected message with the correct values.
   */
  private static void assertThatEvaluatedMessageEqualsExpectedMessage(
      CandidateEvaluatedMessage expectedEvaluatedMessage, CandidateEvaluatedMessage messageBody) {
    assertThat(messageBody)
        .usingRecursiveComparison()
        .ignoringFields(
            "candidate.topLevelNviOrganizations",
            "candidate.publicationChannelForLevel.name",
            "candidate.publicationDetails.identifier",
            "candidate.publicationDetails.contributors",
            "candidate.publicationDetails.modifiedDate",
            "candidate.publicationDetails.pageCount",
            "candidate.publicationDetails.publicationChannels",
            "candidate.publicationDetails.title",
            "candidate.publicationDetails.topLevelOrganizations")
        .ignoringCollectionOrder()
        .isEqualTo(expectedEvaluatedMessage);
  }

  @Nested
  @DisplayName("Test cases with dynamic test data")
  class evaluateNviCandidatesWithDynamicTestData {
    private SampleExpandedPublicationFactory factory;
    private Organization nviOrganization;
    private PublicationDate publicationDate;

    @BeforeEach
    void setup() {
      publicationDate = randomPublicationDate();
      factory = new SampleExpandedPublicationFactory(scenario).withPublicationDate(publicationDate);
      nviOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    }

    @Test
    void shouldIdentifyCandidateWithOnlyVerifiedNviCreators() {
      setupOpenPeriod(scenario, publicationDate.year());
      var publication =
          factory.withContributor(verifiedCreatorFrom(nviOrganization)).getExpandedPublication();
      var candidate = evaluatePublicationAndGetPersistedCandidate(publication);
      var publicationDetails = candidate.getPublicationDetails();

      assertThat(candidate)
          .extracting(Candidate::isApplicable, Candidate::getCreatorShareCount)
          .containsExactly(true, 1);
      assertThat(publicationDetails.allCreators())
          .hasSize(1)
          .allMatch(VerifiedNviCreatorDto.class::isInstance);
    }

    @Test
    void shouldIdentifyCandidateWithOnlyUnverifiedNviCreators() {
      setupOpenPeriod(scenario, publicationDate.year());
      var publication =
          factory.withContributor(unverifiedCreatorFrom(nviOrganization)).getExpandedPublication();
      var candidate = evaluatePublicationAndGetPersistedCandidate(publication);
      var publicationDetails = candidate.getPublicationDetails();

      assertThat(candidate)
          .extracting(Candidate::isApplicable, Candidate::getTotalPoints)
          .containsExactly(true, ZERO.setScale(SCALE, ROUNDING_MODE));
      assertThat(publicationDetails.allCreators())
          .hasSize(1)
          .allMatch(UnverifiedNviCreatorDto.class::isInstance);
    }

    @Test
    void shouldIdentifyCandidateWithUnnamedVerifiedAuthor() {
      setupOpenPeriod(scenario, publicationDate.year());
      var verifiedContributor = randomContributorDtoBuilder(nviOrganization).withName(null).build();
      var publication = factory.withContributor(verifiedContributor).getExpandedPublication();
      var candidate = evaluatePublicationAndGetPersistedCandidate(publication);

      assertThat(candidate)
          .extracting(Candidate::isApplicable, Candidate::getTotalPoints)
          .containsExactly(true, ONE.setScale(SCALE, ROUNDING_MODE));
    }

    @Test
    void shouldRejectUnverifiedAuthorsWithoutName() {
      setupOpenPeriod(scenario, publicationDate.year());
      var unnamedContributor =
          randomContributorDtoBuilder(nviOrganization)
              .withId(null)
              .withName(null)
              .withVerificationStatus(STATUS_UNVERIFIED)
              .build();
      var publication = factory.withContributor(unnamedContributor).getExpandedPublication();

      handler.handleRequest(createEvaluationEvent(publication), CONTEXT);
      var messageBody = getMessageBody();

      assertThat(messageBody.candidate()).isInstanceOf(UpsertNonNviCandidateRequest.class);
    }

    @Test
    void shouldHandleUnverifiedAuthorsWithMultipleNames() {
      setupOpenPeriod(scenario, publicationDate.year());
      var publication =
          factory.withContributor(createUnverifiedCreatorWithTwoNames()).getExpandedPublication();

      var candidate = evaluatePublicationAndGetPersistedCandidate(publication);
      var publicationDetails = candidate.getPublicationDetails();

      assertThat(candidate)
          .extracting(Candidate::isApplicable, Candidate::getTotalPoints)
          .containsExactly(true, ZERO.setScale(SCALE, ROUNDING_MODE));
      assertThat(publicationDetails.nviCreators())
          .hasSize(1)
          .allMatch(NviCreator.class::isInstance);
    }

    private SampleExpandedContributor createUnverifiedCreatorWithTwoNames() {
      var expandedAffiliations = List.of(mapOrganizationToAffiliation(nviOrganization));
      return SampleExpandedContributor.builder()
          .withId(null)
          .withNames(List.of("Ignacio N. Kognito", "I.N. Kognito"))
          .withRole(ROLE_CREATOR.getValue())
          .withOrcId(randomString())
          .withVerificationStatus(STATUS_UNVERIFIED.getValue())
          .withAffiliations(expandedAffiliations)
          .build();
    }

    @Test
    void shouldIdentifyCandidateWithBothVerifiedAndUnverifiedNviCreators() {
      setupOpenPeriod(scenario, publicationDate.year());
      var publication =
          factory
              .withContributor(verifiedCreatorFrom(nviOrganization))
              .withContributor(unverifiedCreatorFrom(nviOrganization))
              .getExpandedPublication();
      var candidate = evaluatePublicationAndGetPersistedCandidate(publication);
      var publicationDetails = candidate.getPublicationDetails();

      var expectedTotalPoints = BigDecimal.valueOf(0.7071).setScale(SCALE, ROUNDING_MODE);
      assertThat(candidate)
          .extracting(
              Candidate::isApplicable,
              Candidate::getTotalPoints,
              Candidate::getCreatorShareCount,
              Candidate::getNviCreatorAffiliations)
          .containsExactly(true, expectedTotalPoints, 2, List.of(nviOrganization.id()));
      assertThat(publicationDetails.unverifiedCreators()).hasSize(1);
      assertThat(publicationDetails.verifiedCreators()).hasSize(1);
    }

    @Test
    void shouldIdentifyCandidateWithMissingCountryCode() {
      setupOpenPeriod(scenario, publicationDate.year());
      var organizationWithoutCountryCode = factory.setupTopLevelOrganization(null, true);
      var publication =
          factory
              .withContributor(verifiedCreatorFrom(organizationWithoutCountryCode))
              .getExpandedPublication();
      var candidate = evaluatePublicationAndGetPersistedCandidate(publication);

      assertThat(candidate.getCreatorShareCount()).isEqualTo(1);
      assertThat(candidate.getTotalPoints()).isPositive();
      assertThat(candidate.isApplicable()).isTrue();
    }

    @Test
    void shouldRejectCandidateWithOnlySwedishCountryCode() {
      setupOpenPeriod(scenario, publicationDate.year());
      var swedishOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_SWEDEN, true);
      var publication =
          factory
              .withContributor(verifiedCreatorFrom(swedishOrganization))
              .getExpandedPublication();

      handler.handleRequest(createEvaluationEvent(publication), CONTEXT);
      var messageBody = getMessageBody();

      assertThat(messageBody.candidate()).isInstanceOf(UpsertNonNviCandidateRequest.class);
    }

    @Test
    void shouldEvaluateCandidateInOpenPeriod() {
      // Given a publication that fulfills all criteria for NVI reporting
      // And the publication is published in an open period
      // When the publication is evaluated
      // Then it should be evaluated as a Candidate
      setupOpenPeriod(scenario, publicationDate.year());
      var publication =
          factory.withContributor(verifiedCreatorFrom(nviOrganization)).getExpandedPublication();

      var candidate = evaluatePublicationAndGetPersistedCandidate(publication);
      var publicationDetails = candidate.getPublicationDetails();

      assertThat(candidate.getTotalPoints()).isPositive();
      assertThat(candidate.isApplicable()).isTrue();
      assertThat(publicationDetails.publicationDate()).isEqualTo(publicationDate);
    }

    @Test
    void shouldEvaluateExistingCandidateInClosedPeriod() {
      // Given a publication that has been evaluated as an applicable Candidate
      // And the publication is published in a closed period
      // When the publication is evaluated
      // Then it should be evaluated as a Candidate
      setupClosedPeriod(scenario, publicationDate.year());
      var publication =
          factory.withContributor(verifiedCreatorFrom(nviOrganization)).getExpandedPublication();
      setupCandidateMatchingPublication(publication);

      var candidate = evaluatePublicationAndGetPersistedCandidate(publication);
      var publicationDetails = candidate.getPublicationDetails();

      assertThat(candidate.getTotalPoints()).isPositive();
      assertThat(candidate.isApplicable()).isTrue();
      assertThat(publicationDetails.publicationDate()).isEqualTo(publicationDate);
    }

    @Test
    void shouldEvaluateExistingCandidateInClosedPeriodThatIsNoLongerApplicable() {
      // Given a publication that has been evaluated as an applicable Candidate
      // And the publication is published in a closed period
      // When the publication is updated to be no longer applicable
      // Then it should be re-evaluated as a NonCandidate
      setupClosedPeriod(scenario, publicationDate.year());
      var publicationFactory = factory.withContributor(verifiedCreatorFrom(nviOrganization));
      setupCandidateMatchingPublication(publicationFactory.getExpandedPublication());

      var updatedPublication =
          publicationFactory.withPublicationType("ComicBook").getExpandedPublication();
      handler.handleRequest(createEvaluationEvent(updatedPublication), CONTEXT);
      var messageBody = getMessageBody();

      assertThat(messageBody.candidate()).isInstanceOf(UpsertNonNviCandidateRequest.class);
    }

    @Test
    void shouldEvaluateNewPublicationAsNonCandidateInClosedPeriod() {
      // Given a publication that fulfills all criteria for NVI reporting except for the publication
      // year
      // And the publication is published in a closed period
      // And the publication is not already a Candidate
      // When the publication is evaluated
      // Then it should be evaluated as a NonCandidate
      setupClosedPeriod(scenario, publicationDate.year());
      var publication =
          factory.withContributor(verifiedCreatorFrom(nviOrganization)).getExpandedPublication();

      handler.handleRequest(createEvaluationEvent(publication), CONTEXT);
      var messageBody = getMessageBody();

      assertThat(messageBody.candidate()).isInstanceOf(UpsertNonNviCandidateRequest.class);
    }

    @Test
    void shouldEvaluatePublicationAsNonCandidateIfPeriodDoesNotExist() {
      // Given a publication that fulfills all criteria for NVI reporting except for the publication
      // year
      // And the publication is published before the first registered NVI period
      // When the publication is evaluated
      // Then it should be evaluated as a NonCandidate
      var historicalDate = new PublicationDate("2000", null, null);
      var publication =
          factory
              .withContributor(verifiedCreatorFrom(nviOrganization))
              .withPublicationDate(historicalDate)
              .getExpandedPublication();

      handler.handleRequest(createEvaluationEvent(publication), CONTEXT);
      var messageBody = getMessageBody();
      var nviPeriod = periodService.findByPublishingYear(historicalDate.year());

      assertThat(messageBody.candidate()).isInstanceOf(UpsertNonNviCandidateRequest.class);
      assertTrue(nviPeriod.isEmpty());
    }

    @Test
    void shouldNotEvaluateReportedCandidate() {
      // Given a publication that fulfills all criteria for NVI reporting
      // And the publication is already a Candidate
      // And the publication is already Reported
      // When the publication is evaluated
      // Then the evaluation should be skipped
      // And the Candidate entry in the database should not be updated
      setupClosedPeriod(scenario, publicationDate.year());
      var existingCandidateDao =
          setupReportedCandidate(candidateRepository, publicationDate.year());
      var publication =
          factory
              .withContributor(verifiedCreatorFrom(nviOrganization))
              .getExpandedPublicationBuilder()
              .withId(existingCandidateDao.candidate().publicationId())
              .build();

      handler.handleRequest(createEvaluationEvent(publication), CONTEXT);

      assertEquals(0, queueClient.getSentMessages().size());
    }

    @Test
    void shouldReEvaluatePublicationMovedToFuturePeriod() {
      // Given a publication that is an applicable Candidate
      // And the publication is published in an open period
      // When the publication date is updated to a year with no registered NVI period
      // Then the publication should be re-evaluated as a NonCandidate
      var openPeriod = randomPublicationDateInYear(CURRENT_YEAR);
      var nonPeriod = randomPublicationDateInYear(CURRENT_YEAR + 1);
      setupOpenPeriod(scenario, openPeriod.year());
      var publicationFactory =
          factory
              .withContributor(verifiedCreatorFrom(nviOrganization))
              .withPublicationDate(openPeriod);
      setupCandidateMatchingPublication(publicationFactory.getExpandedPublication());

      var updatedPublication =
          publicationFactory.withPublicationDate(nonPeriod).getExpandedPublication();
      handler.handleRequest(createEvaluationEvent(updatedPublication), CONTEXT);
      var messageBody = getMessageBody();

      assertThat(messageBody.candidate()).isInstanceOf(UpsertNonNviCandidateRequest.class);
    }

    @Test
    void shouldReEvaluatePublicationMovedFromFuturePeriodToOpenPeriod() {
      // Given a publication that is an applicable Candidate
      // And the publication is published in an open period
      // When the publication date is updated to a year with no registered NVI period
      // And the publication date is updated to a year with an open NVI period
      // Then the publication should be re-evaluated as a Candidate
      var openPeriod = randomPublicationDateInYear(CURRENT_YEAR);
      var nonPeriod = randomPublicationDateInYear(CURRENT_YEAR + 2);
      var newPeriod = randomPublicationDateInYear(CURRENT_YEAR + 1);
      setupOpenPeriod(scenario, openPeriod.year());
      setupOpenPeriod(scenario, newPeriod.year());
      var publicationFactory = factory.withContributor(verifiedCreatorFrom(nviOrganization));

      var originalPublication =
          publicationFactory.withPublicationDate(openPeriod).getExpandedPublication();
      evaluatePublicationAndPersistResult(originalPublication.toJsonString());

      var updatedPublication =
          publicationFactory.withPublicationDate(nonPeriod).getExpandedPublication();
      evaluatePublicationAndPersistResult(updatedPublication.toJsonString());

      var finalPublication =
          publicationFactory.withPublicationDate(newPeriod).getExpandedPublication();
      var updatedCandidate = evaluatePublicationAndGetPersistedCandidate(finalPublication);
      var publicationDetails = updatedCandidate.getPublicationDetails();

      assertThat(updatedCandidate.isApplicable()).isTrue();
      assertThat(publicationDetails.publicationDate()).isEqualTo(newPeriod);
    }

    private SQSEvent createEvaluationEvent(SampleExpandedPublication publication) {
      try {
        var fileUri = addPublicationToS3(publication);
        return createEvent(new PersistedResourceMessage(fileUri));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private URI addPublicationToS3(SampleExpandedPublication publication) throws IOException {

      return s3Driver.insertFile(
          UnixPath.of(publication.identifier().toString()), publication.toJsonString());
    }

    private void setupCandidateMatchingPublication(
        SampleExpandedPublication sampleExpandedPublication) {
      var year = sampleExpandedPublication.publicationDate().year();
      var upsertCandidateRequest =
          randomUpsertRequestBuilder()
              .withPublicationDate(new PublicationDateDto(year, null, null))
              .withPublicationId(sampleExpandedPublication.id())
              .build();
      candidateService.upsert(upsertCandidateRequest);
    }
  }
}
