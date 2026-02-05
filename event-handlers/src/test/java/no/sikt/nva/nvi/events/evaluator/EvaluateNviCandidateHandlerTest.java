package no.sikt.nva.nvi.events.evaluator;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.EVALUATION_DLQ_URL;
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
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDate;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateInYear;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static no.sikt.nva.nvi.test.TestConstants.CRISTIN_NVI_ORG_TOP_LEVEL_ID;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_PUBLICATION_ID;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.model.PublicationChannel;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.queue.NviReceiveMessage;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.test.SampleExpandedContributor;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.unit.nva.identifiers.SortableIdentifier;
import nva.commons.apigateway.exceptions.ApiGatewayException;
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

class EvaluateNviCandidateHandlerTest extends EvaluationTest {

  public static final URI HARDCODED_PUBLICATION_CHANNEL_ID =
      URI.create("https://api.dev.nva.aws.unit.no/publication-channels/series/490845/2023");
  private static final String ACADEMIC_CHAPTER_PATH = "evaluator/candidate_academicChapter.json";

  private static final String ACADEMIC_LITERATURE_REVIEW_JSON_PATH =
      "evaluator/candidate_academicLiteratureReview" + ".json";
  private static final String ACADEMIC_MONOGRAPH_JSON_PATH =
      "evaluator/candidate_academicMonograph.json";
  private static final String ACADEMIC_COMMENTARY_JSON_PATH =
      "evaluator/candidate_academicCommentary.json";
  private static final String ACADEMIC_ARTICLE_PATH = "evaluator/candidate_academicArticle.json";
  private static final String ACADEMIC_ARTICLE = getPublicationFromFile(ACADEMIC_ARTICLE_PATH);

  private static String getPublicationFromFile(String path, URI publicationId) {
    var identifier = SortableIdentifier.fromUri(publicationId);
    return stringFromResources(Path.of(path))
        .replace("__REPLACE_WITH_PUBLICATION_ID__", publicationId.toString())
        .replace("__REPLACE_WITH_PUBLICATION_IDENTIFIER__", identifier.toString());
  }

  private static String getPublicationFromFile(String path) {
    return getPublicationFromFile(path, HARDCODED_PUBLICATION_ID);
  }

  @Test
  void shouldCreateNewCandidateForApplicablePublication() {
    var publication = getPublicationFromFile("evaluator/candidate.json");

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    assertThat(candidate.isApplicable()).isTrue();
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
        getPublicationFromFile("evaluator/candidate_publicationDate_replace_year.json")
            .replace("__REPLACE_YEAR__", "1948-1997");
    var documentWithMissingDate = getPublicationFromFile("expandedPublications/invalidDraft.json");
    return Stream.of(
        argumentSet("Malformed publication year", documentWithMalformedDate),
        argumentSet("Missing publication date", documentWithMissingDate));
  }

  @Test
  void shouldEvaluateExistingCandidateInOpenPeriod() {
    setupOpenPeriod(scenario, THIS_YEAR);
    var publication = createApplicablePublication(THIS_YEAR);
    var candidate = setupExistingCandidateForPublication(publication);

    var updatedAbstract = randomString();
    var updatedPublication =
        publication.getExpandedPublicationBuilder().withAbstract(updatedAbstract).build();
    handleEvaluation(updatedPublication.toJsonString());

    var updatedCandidate = candidateService.getCandidateByPublicationId(updatedPublication.id());
    assertThat(updatedCandidate.publicationDetails().abstractText()).isEqualTo(updatedAbstract);
    assertThat(updatedCandidate.modifiedDate()).isAfter(candidate.modifiedDate());
  }

  @Test
  void shouldEvaluateStrippedCandidate() {
    var publication = getPublicationFromFile("evaluator/candidate_stripped.json");

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    assertThat(candidate.isApplicable()).isTrue();
  }

  @Test
  void shouldCreateNewCandidateWithPointsOnlyForNviInstitutions() {
    var publication =
        getPublicationFromFile("evaluator/candidate_verifiedCreator_with_nonNviInstitution.json");

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    assertThat(candidate.getInstitutionPoints())
        .singleElement()
        .extracting(InstitutionPoints::institutionId)
        .isEqualTo(CRISTIN_NVI_ORG_TOP_LEVEL_ID);
  }

  @Test
  void shouldCreateNewCandidateEventOnValidAcademicArticle() {
    handleEvaluation(ACADEMIC_ARTICLE);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);

    var expectedPoints = ONE.setScale(SCALE, ROUNDING_MODE);
    assertThat(candidate)
        .extracting(
            Candidate::isApplicable,
            Candidate::getPublicationType,
            Candidate::getTotalPoints,
            Candidate::getBasePoints)
        .containsExactly(true, InstanceType.ACADEMIC_ARTICLE, expectedPoints, expectedPoints);
    assertThat(candidate.getPublicationChannel().channelType()).isEqualTo(JOURNAL);
    assertThat(candidate.getPublicationChannel().scientificValue())
        .isEqualTo(ScientificValue.LEVEL_ONE);
  }

  @Test
  void shouldCreateNewCandidateForValidAcademicChapter() {
    var publication = getPublicationFromFile(ACADEMIC_CHAPTER_PATH);

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    var expectedPoints = ONE.setScale(SCALE, ROUNDING_MODE);
    assertThat(candidate)
        .extracting(
            Candidate::isApplicable,
            Candidate::getPublicationType,
            Candidate::getTotalPoints,
            Candidate::getBasePoints)
        .containsExactly(true, InstanceType.ACADEMIC_CHAPTER, expectedPoints, expectedPoints);
    assertThat(candidate.getPublicationChannel().channelType()).isEqualTo(SERIES);
    assertThat(candidate.getPublicationChannel().scientificValue())
        .isEqualTo(ScientificValue.LEVEL_ONE);
  }

  @Test
  void shouldCreateNewCandidateForValidAcademicMonograph() {
    var publication = getPublicationFromFile(ACADEMIC_MONOGRAPH_JSON_PATH);

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    var expectedPoints = BigDecimal.valueOf(5).setScale(SCALE, ROUNDING_MODE);
    assertThat(candidate)
        .extracting(
            Candidate::isApplicable,
            Candidate::getPublicationType,
            Candidate::getTotalPoints,
            Candidate::getBasePoints)
        .containsExactly(true, ACADEMIC_MONOGRAPH, expectedPoints, expectedPoints);
    assertThat(candidate.getPublicationChannel().channelType()).isEqualTo(SERIES);
    assertThat(candidate.getPublicationChannel().scientificValue())
        .isEqualTo(ScientificValue.LEVEL_ONE);
  }

  @Test
  void shouldCreateNewCandidateForValidAcademicCommentary() {
    var publication = getPublicationFromFile(ACADEMIC_COMMENTARY_JSON_PATH);

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    var expectedPoints = BigDecimal.valueOf(5).setScale(SCALE, ROUNDING_MODE);
    assertThat(candidate)
        .extracting(
            Candidate::isApplicable,
            Candidate::getPublicationType,
            Candidate::getTotalPoints,
            Candidate::getBasePoints)
        .containsExactly(true, ACADEMIC_COMMENTARY, expectedPoints, expectedPoints);
    assertThat(candidate.getPublicationChannel().channelType()).isEqualTo(SERIES);
    assertThat(candidate.getPublicationChannel().scientificValue())
        .isEqualTo(ScientificValue.LEVEL_ONE);
  }

  @Test
  void shouldCreateNewCandidateForValidAcademicLiteratureReview() {
    var publication = getPublicationFromFile(ACADEMIC_LITERATURE_REVIEW_JSON_PATH);

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    var expectedPoints = BigDecimal.valueOf(1).setScale(SCALE, ROUNDING_MODE);
    assertThat(candidate)
        .extracting(
            Candidate::isApplicable,
            Candidate::getPublicationType,
            Candidate::getTotalPoints,
            Candidate::getBasePoints)
        .containsExactly(true, ACADEMIC_LITERATURE_REVIEW, expectedPoints, expectedPoints);
    assertThat(candidate.getPublicationChannel().channelType()).isEqualTo(JOURNAL);
    assertThat(candidate.getPublicationChannel().scientificValue())
        .isEqualTo(ScientificValue.LEVEL_ONE);
  }

  @Test
  void shouldCalculatePointsOnValidAcademicArticle() {
    handleEvaluation(ACADEMIC_ARTICLE);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    assertThat(candidate.getInstitutionPoints()).isNotEmpty();
    assertThat(candidate.getPointValueForInstitution(CRISTIN_NVI_ORG_TOP_LEVEL_ID))
        .isEqualTo(BigDecimal.valueOf(1).setScale(4, RoundingMode.HALF_UP));
  }

  @Test
  void shouldCreateInstitutionApprovalsForTopLevelInstitutions() {
    handleEvaluation(ACADEMIC_ARTICLE);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    assertThat(candidate.getInstitutionPoints()).isNotEmpty();
    assertThat(candidate.getPointValueForInstitution(CRISTIN_NVI_ORG_TOP_LEVEL_ID)).isNotNull();
    assertThat(candidate.approvals()).containsKey(CRISTIN_NVI_ORG_TOP_LEVEL_ID);
  }

  @Test
  void
      shouldCreateNewCandidateEventOnValidAcademicChapterWithSeriesLevelUnassignedWithPublisherLevel() {
    var publication =
        getPublicationFromFile(
            "evaluator/candidate_academicChapter_seriesLevelUnassignedPublisherLevelOne.json");

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    assertThat(candidate.isApplicable()).isTrue();
  }

  @Test
  void
      shouldCreateNewCandidateEventOnValidAcademicMonographWithSeriesLevelUnassignedWithPublisherLevel() {
    var publication =
        getPublicationFromFile(
            "evaluator/candidate_academicMonograph_seriesLevelUnassignedPublisherLevelOne.json");

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    assertThat(candidate.isApplicable()).isTrue();
  }

  @Test
  void
      shouldCreateNewCandidateEventOnValidAcademicCommentaryWithoutSeriesLevelWithPublisherLevel() {
    var publication =
        getPublicationFromFile("evaluator/candidate_academicCommentary_withoutSeries.json");

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    assertThat(candidate.isApplicable()).isTrue();
  }

  @Test
  void shouldCreateNewCandidateEventOnValidAcademicMonograph() {
    var publication = getPublicationFromFile(ACADEMIC_MONOGRAPH_JSON_PATH);

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    assertThat(candidate.isApplicable()).isTrue();
    assertThat(candidate.getPublicationType()).isEqualTo(ACADEMIC_MONOGRAPH);
  }

  @Test
  void shouldCreateNewCandidateEventOnValidAcademicLiteratureReview() {
    var publication = getPublicationFromFile(ACADEMIC_LITERATURE_REVIEW_JSON_PATH);

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    assertThat(candidate.isApplicable()).isTrue();
    assertThat(candidate.getPublicationType()).isEqualTo(ACADEMIC_LITERATURE_REVIEW);
  }

  @ParameterizedTest
  @MethodSource("nonApplicablePublicationProvider")
  void shouldNotCreateCandidateForNonApplicablePublication(String filePath) {
    var publication = getPublicationFromFile(filePath);

    handleEvaluation(publication);

    assertThrows(
        CandidateNotFoundException.class,
        () -> candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID));
  }

  private static Stream<Arguments> nonApplicablePublicationProvider() {
    return Stream.of(
        argumentSet(
            "AcademicChapter in series with level zero",
            "evaluator/nonCandidate_academicChapter_seriesLevelZero.json"),
        argumentSet(
            "AcademicCommentary in series with level zero",
            "evaluator/nonCandidate_academicCommentary_seriesLevelZero.json"),
        argumentSet(
            "AcademicMonograph in series with level zero",
            "evaluator/nonCandidate_notValidMonographArticle.json"),
        argumentSet("Publication status is DRAFT", "evaluator/nonCandidate_notPublished.json"),
        argumentSet(
            "Publication type is MusicPerformance", "evaluator/nonCandidate_musicalArts.json"));
  }

  @Test
  void shouldPlaceSqsMessageWithExceptionDetailOnDlqWhenEvaluationFails() {
    var originalMessage = new PersistedResourceMessage(UriWrapper.fromUri("s3://dummy").getUri());
    var event = createEvent(originalMessage);

    handler.handleRequest(event, CONTEXT);

    var dlqMessage = fetchMessageFromDlq();
    assertThat(dlqMessage.body()).isEqualToIgnoringWhitespace(originalMessage.toJsonString());
    assertThat(dlqMessage.messageAttributes().get("errorMessage")).isNotBlank();
    assertThat(dlqMessage.messageAttributes().get("errorType")).isNotBlank();
    assertThat(dlqMessage.messageAttributes().get("stackTrace")).isNotBlank();
  }

  @Test
  void shouldNotCreateCandidateWhenPublicationHasZeroNviInstitutions() {
    mockGetAllCustomersResponse(emptyList());

    handler = createHandler();
    handleEvaluation(ACADEMIC_ARTICLE);

    assertThrows(
        CandidateNotFoundException.class,
        () -> candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID));
  }

  @Test
  void shouldSendMessageToDlqWhenProblemsFetchingCustomer()
      throws IOException, ApiGatewayException {
    var errorMessage = "Internal server error";
    doThrow(new RuntimeException(errorMessage)).when(identityServiceClient).getAllCustomers();
    var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
    var originalMessage = new PersistedResourceMessage(fileUri);
    var event = createEvent(originalMessage);

    handler.handleRequest(event, CONTEXT);

    var dlqMessage = fetchMessageFromDlq();
    assertThat(dlqMessage.body()).isEqualToIgnoringWhitespace(originalMessage.toJsonString());
    assertThat(dlqMessage.messageAttributes().get("errorMessage")).contains(errorMessage);
  }

  private NviReceiveMessage fetchMessageFromDlq() {
    return queueClient.receiveMessage(EVALUATION_DLQ_URL.getValue(), 1).messages().getFirst();
  }

  @Test
  void shouldCreateNewCandidateEventWhenAffiliationAreNviInstitutions() {
    handleEvaluation(ACADEMIC_ARTICLE);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
    assertThat(candidate.isApplicable()).isTrue();
  }

  @Test
  @Deprecated
  void shouldHandleSeriesWithMultipleTypes() {
    var publication =
        getPublicationFromFile("evaluator/candidate_academicMonograph_series_multiple_types.json");
    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
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
    var publication =
        getPublicationFromFile("evaluator/candidate_academicArticle_journal_multiple_types.json");

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(HARDCODED_PUBLICATION_ID);
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
      mockGetAllCustomersResponse(factory.getCustomerOrganizations());
    }

    @Test
    void shouldIdentifyCandidateWithOnlyVerifiedNviCreators() {
      setupOpenPeriod(scenario, publicationDate.year());
      var publication = factory.withContributor(verifiedCreatorFrom(nviOrganization));
      mockGetAllCustomersResponse(factory.getCustomerOrganizations());

      handleEvaluation(publication);

      var candidate = candidateService.getCandidateByPublicationId(publication.getPublicationId());
      var publicationDetails = candidate.publicationDetails();
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
      var publication = factory.withContributor(unverifiedCreatorFrom(nviOrganization));
      mockGetAllCustomersResponse(factory.getCustomerOrganizations());

      handleEvaluation(publication);

      var candidate = candidateService.getCandidateByPublicationId(publication.getPublicationId());

      var publicationDetails = candidate.publicationDetails();
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
      var publication = factory.withContributor(verifiedContributor);
      mockGetAllCustomersResponse(factory.getCustomerOrganizations());

      handleEvaluation(publication);

      var candidate = candidateService.getCandidateByPublicationId(publication.getPublicationId());

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

      handleEvaluation(publication.toJsonString());

      assertThrows(
          CandidateNotFoundException.class,
          () -> candidateService.getCandidateByPublicationId(publication.id()));
    }

    @Test
    void shouldHandleUnverifiedAuthorsWithMultipleNames() {
      setupOpenPeriod(scenario, publicationDate.year());
      var publication = factory.withContributor(createUnverifiedCreatorWithTwoNames());
      mockGetAllCustomersResponse(factory.getCustomerOrganizations());

      handleEvaluation(publication);

      var candidate = candidateService.getCandidateByPublicationId(publication.getPublicationId());
      var publicationDetails = candidate.publicationDetails();
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
              .withContributor(unverifiedCreatorFrom(nviOrganization));

      handleEvaluation(publication);

      var candidate = candidateService.getCandidateByPublicationId(publication.getPublicationId());

      var publicationDetails = candidate.publicationDetails();
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
          factory.withContributor(verifiedCreatorFrom(organizationWithoutCountryCode));

      handleEvaluation(publication);

      var candidate = candidateService.getCandidateByPublicationId(publication.getPublicationId());

      assertThat(candidate.getCreatorShareCount()).isEqualTo(1);
      assertThat(candidate.getTotalPoints()).isPositive();
      assertThat(candidate.isApplicable()).isTrue();
    }

    @Test
    void shouldRejectCandidateWithOnlySwedishCountryCode() {
      setupOpenPeriod(scenario, publicationDate.year());
      var swedishOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_SWEDEN, false);
      var publication = factory.withContributor(verifiedCreatorFrom(swedishOrganization));

      handleEvaluation(publication);

      assertThrows(
          CandidateNotFoundException.class,
          () -> candidateService.getCandidateByPublicationId(publication.getPublicationId()));
    }

    @Test
    void shouldEvaluateCandidateInOpenPeriod() {
      // Given a publication that fulfills all criteria for NVI reporting
      // And the publication is published in an open period
      // When the publication is evaluated
      // Then it should be evaluated as a Candidate
      setupOpenPeriod(scenario, publicationDate.year());
      var publication = factory.withContributor(verifiedCreatorFrom(nviOrganization));
      mockGetAllCustomersResponse(factory.getCustomerOrganizations());

      handleEvaluation(publication);

      var candidate = candidateService.getCandidateByPublicationId(publication.getPublicationId());
      var publicationDetails = candidate.publicationDetails();
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
      setupOpenPeriod(scenario, publicationDate.year());
      var publicationFactory = factory.withContributor(verifiedCreatorFrom(nviOrganization));
      setupCandidateMatchingPublication(publicationFactory.getExpandedPublication());
      setupClosedPeriod(scenario, publicationDate.year());

      var publication = publicationFactory.withPublicationType("ComicBook");
      handleEvaluation(publication);

      var candidate = candidateService.getCandidateByPublicationId(publication.getPublicationId());
      assertThat(candidate.isApplicable()).isFalse();
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
      mockGetAllCustomersResponse(factory.getCustomerOrganizations());

      handleEvaluation(publication.toJsonString());

      assertThatNoCandidateExistsForPublication(publication.id());
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
      mockGetAllCustomersResponse(factory.getCustomerOrganizations());

      handleEvaluation(publication.toJsonString());
      var nviPeriod = periodService.findByPublishingYear(historicalDate.year());

      assertThatNoCandidateExistsForPublication(publication.id());
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
      var publicationId = existingCandidateDao.candidate().publicationId();
      var publication =
          factory
              .withContributor(verifiedCreatorFrom(nviOrganization))
              .getExpandedPublicationBuilder()
              .withId(publicationId)
              .build();
      mockGetAllCustomersResponse(factory.getCustomerOrganizations());
      var fileUri = scenario.setupExpandedPublicationInS3(publication.toJsonString());
      var event = createEvent(new PersistedResourceMessage(fileUri));

      handler.handleRequest(event, CONTEXT);

      var candidate = candidateService.getCandidateByPublicationId(publicationId);
      assertEquals(0, queueClient.getSentMessages().size());
      assertThat(candidate.isReported()).isTrue();
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
      handleEvaluation(updatedPublication.toJsonString());

      assertThatPublicationIsNonCandidate(updatedPublication.id());
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

      var originalPublication =
          factory
              .withContributor(verifiedCreatorFrom(nviOrganization))
              .withPublicationDate(openPeriod);
      handleEvaluation(originalPublication);

      handleEvaluation(originalPublication.withPublicationDate(nonPeriod));

      handleEvaluation(originalPublication.withPublicationDate(newPeriod));

      var updatedCandidate =
          candidateService.getCandidateByPublicationId(factory.getPublicationId());
      var publicationDetails = updatedCandidate.publicationDetails();
      assertThat(updatedCandidate.isApplicable()).isTrue();
      assertThat(publicationDetails.publicationDate()).isEqualTo(newPeriod);
    }

    private void setupCandidateMatchingPublication(
        SampleExpandedPublication sampleExpandedPublication) {
      var upsertCandidateRequest =
          randomUpsertRequestBuilder()
              .withPublicationDate(getPublicationDateDto(sampleExpandedPublication))
              .withPublicationId(sampleExpandedPublication.id())
              .build();
      candidateService.upsertCandidate(upsertCandidateRequest);
    }
  }

  private static PublicationDateDto getPublicationDateDto(
      SampleExpandedPublication sampleExpandedPublication) {
    var publicationDate = sampleExpandedPublication.publicationDate();
    return new PublicationDateDto(
        publicationDate.year(), publicationDate.month(), publicationDate.day());
  }
}
