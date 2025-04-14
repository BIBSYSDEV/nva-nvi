package no.sikt.nva.nvi.events.evaluator;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_JSON_PUBLICATION_DATE;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import nva.commons.core.paths.UnixPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// FIXME: Suppressing AvoidDuplicateLiterals because I don't want to touch the test data now.
// We should consider moving the test data to a separate CSV file or something later.
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class PointCalculationTest extends EvaluationTest {
  private SampleExpandedPublicationFactory factory;
  private Organization nviOrganization1;
  private Organization nviOrganization2;
  private Organization nonNviOrganization;

  @BeforeEach
  void setup() {
    var year = HARDCODED_JSON_PUBLICATION_DATE.year();
    setupOpenPeriod(scenario, year);
    factory = new SampleExpandedPublicationFactory(authorizedBackendUriRetriever, uriRetriever);

    // Set up default organizations suitable for most test cases
    nviOrganization1 = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    nviOrganization2 = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    nonNviOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_SWEDEN, false);
  }

  @ParameterizedTest(
      name =
          "Should calculate points correctly single contributor affiliated with a single "
              + "institution. No international collaboration.")
  @MethodSource("singleCreatorSingleInstitutionPointProvider")
  void shouldCalculateNviPointsForSingleInstitution(PointParameters parameters) {
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1)
            .withPublicationType(parameters.instanceType())
            .withPublicationChannel(parameters.channelType(), parameters.level())
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    assertThatPointValuesMatch(parameters, candidate);
  }

  @ParameterizedTest(
      name =
          "Should calculate points correctly for co-publishing cases where two creators are "
              + "affiliated with one of the two institutions involved.")
  @MethodSource("twoCreatorsAffiliatedWithOneInstitutionPointProvider")
  void shouldCalculateNviPointsForCoPublishingTwoCreatorsAffiliatedWithOneInstitution(
      PointParameters parameters) {
    var nonNviCreatorCount = parameters.creatorShareCount() - 3;
    var nonNviCreatorCountryCode =
        parameters.isInternationalCollaboration() ? COUNTRY_CODE_SWEDEN : COUNTRY_CODE_NORWAY;
    nonNviOrganization = factory.setupTopLevelOrganization(nonNviCreatorCountryCode, false);
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1, nviOrganization2, nonNviOrganization)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1, nviOrganization2)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1)
            .withRandomCreatorsAffiliatedWith(
                nonNviCreatorCount, nonNviCreatorCountryCode, nonNviOrganization)
            .withPublicationType(parameters.instanceType())
            .withPublicationChannel(parameters.channelType(), parameters.level())
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    assertThatPointValuesMatch(parameters, candidate);
  }

  @ParameterizedTest(
      name =
          "Should calculate points correctly for co-publishing cases where two creators are "
              + "affiliated with two different institutions.")
  @MethodSource("twoCreatorsAffiliatedWithTwoDifferentInstitutionsPointProvider")
  void shouldCalculateNviPointsForCoPublishingTwoCreatorsTwoInstitutionsV2(
      PointParameters parameters) {
    var nonNviCreatorCount = parameters.creatorShareCount() - 2;
    var nonNviCreatorCountryCode =
        parameters.isInternationalCollaboration() ? COUNTRY_CODE_SWEDEN : COUNTRY_CODE_NORWAY;
    nonNviOrganization = factory.setupTopLevelOrganization(nonNviCreatorCountryCode, false);
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1, nviOrganization2, nonNviOrganization)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1)
            .withNorwegianCreatorAffiliatedWith(nviOrganization2)
            .withRandomCreatorsAffiliatedWith(
                nonNviCreatorCount, nonNviCreatorCountryCode, nonNviOrganization)
            .withPublicationType(parameters.instanceType())
            .withPublicationChannel(parameters.channelType(), parameters.level())
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    assertThatPointValuesMatch(parameters, candidate);
  }

  @Test
  void shouldCountTotalCreatorSharesForTopLevelAffiliations() {
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1.hasPart())
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    var expectedPoints = asBigDecimal("1");
    assertEquals(expectedPoints, candidate.totalPoints());
    assertThat(candidate.institutionPoints())
        .extracting(InstitutionPoints::institutionId, InstitutionPoints::institutionPoints)
        .containsExactlyInAnyOrder(tuple(nviOrganization1.id(), expectedPoints));
  }

  @Test
  void shouldNotGiveInternationalPointsIfNonCreatorAffiliatedWithInternationalInstitution() {
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1, nonNviOrganization)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1.hasPart())
            .withRandomNonCreatorsAffiliatedWith(1, COUNTRY_CODE_SWEDEN, nonNviOrganization)
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    var expectedPoints = asBigDecimal("1");
    assertEquals(expectedPoints, candidate.totalPoints());
    assertThat(candidate.institutionPoints())
        .extracting(InstitutionPoints::institutionId, InstitutionPoints::institutionPoints)
        .containsExactlyInAnyOrder(tuple(nviOrganization1.id(), expectedPoints));
  }

  @Test
  void shouldNotCountCreatorSharesForNonCreators() {
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1, nviOrganization2)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1.hasPart())
            .withNonCreatorAffiliatedWith(COUNTRY_CODE_NORWAY, nviOrganization2)
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    var expectedPoints = asBigDecimal("1");
    assertEquals(expectedPoints, candidate.totalPoints());
    assertThat(candidate.institutionPoints())
        .extracting(InstitutionPoints::institutionId, InstitutionPoints::institutionPoints)
        .containsExactlyInAnyOrder(tuple(nviOrganization1.id(), expectedPoints));
  }

  @Test
  void shouldCountOneCreatorShareForCreatorsWithoutAffiliations() {
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1, nviOrganization2)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1.hasPart())
            .withCreatorAffiliatedWith(COUNTRY_CODE_NORWAY)
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    var expectedPoints = asBigDecimal("0.7071");
    assertEquals(expectedPoints, candidate.totalPoints());
    assertThat(candidate.institutionPoints())
        .extracting(InstitutionPoints::institutionId, InstitutionPoints::institutionPoints)
        .containsExactlyInAnyOrder(tuple(nviOrganization1.id(), expectedPoints));
  }

  @Test
  void shouldNotCountCreatorSharesForAffiliationsWithoutId() {
    var organizationWithoutId1 =
        Organization.builder().withCountryCode(COUNTRY_CODE_NORWAY).build();
    var organizationWithoutId2 =
        Organization.builder().withCountryCode(COUNTRY_CODE_NORWAY).build();
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1)
            .withNorwegianCreatorAffiliatedWith(
                nviOrganization1, organizationWithoutId1, organizationWithoutId2)
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);

    var expectedPoints = asBigDecimal("1");
    assertThat(candidate)
        .hasFieldOrPropertyWithValue("totalPoints", expectedPoints)
        .hasFieldOrPropertyWithValue("creatorShareCount", 1)
        .extracting(NviCandidate::institutionPoints)
        .extracting(List::getFirst)
        .hasFieldOrPropertyWithValue("institutionPoints", expectedPoints);
  }

  @Test
  void shouldCountOneCreatorShareForCreatorsWithOnlyAffiliationsWithoutId() {
    var organizationWithoutId1 =
        Organization.builder().withCountryCode(COUNTRY_CODE_NORWAY).build();
    var organizationWithoutId2 =
        Organization.builder().withCountryCode(COUNTRY_CODE_NORWAY).build();
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1)
            .withNorwegianCreatorAffiliatedWith(organizationWithoutId1, organizationWithoutId2)
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    assertThat(candidate)
        .hasFieldOrPropertyWithValue("totalPoints", asBigDecimal("0.7071"))
        .hasFieldOrPropertyWithValue("creatorShareCount", 2)
        .extracting(NviCandidate::institutionPoints)
        .extracting(List::getFirst)
        .hasFieldOrPropertyWithValue("institutionPoints", asBigDecimal("0.7071"));
  }

  @Test
  void shouldCountOneInstitutionShareForCreatorsWithSeveralAffiliationsInSameInstitution() {
    var expectedPoints = getExpectedPointsForSingleContributorWithTwoAffiliations();
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1.hasPart())
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    var actualContributorPoints =
        candidate.institutionPoints().getFirst().creatorAffiliationPoints();
    assertThat(actualContributorPoints)
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("nviCreator")
        .containsExactlyInAnyOrderElementsOf(expectedPoints);
  }

  private List<CreatorAffiliationPoints>
      getExpectedPointsForSingleContributorWithTwoAffiliations() {
    var creatorPoint1 =
        new CreatorAffiliationPoints(
            null, nviOrganization1.hasPart().get(0).id(), asBigDecimal("0.5"));
    var creatorPoint2 =
        new CreatorAffiliationPoints(
            null, nviOrganization1.hasPart().get(1).id(), asBigDecimal("0.5"));
    return List.of(creatorPoint1, creatorPoint2);
  }

  @Test
  void shouldCalculatePointsForCreatorAffiliations() {
    var expectedPoints = getExpectedPointsForTwoContributorsWithThreeAffiliations();
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1, nviOrganization2)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1.hasPart())
            .withNorwegianCreatorAffiliatedWith(nviOrganization1.hasPart().getFirst())
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    var actualPoints = candidate.institutionPoints().getFirst().creatorAffiliationPoints();
    assertThat(actualPoints)
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("nviCreator")
        .containsExactlyInAnyOrderElementsOf(expectedPoints);
  }

  private List<CreatorAffiliationPoints>
      getExpectedPointsForTwoContributorsWithThreeAffiliations() {
    var creatorPoint1 =
        new CreatorAffiliationPoints(
            null, nviOrganization1.hasPart().get(0).id(), asBigDecimal("0.25"));
    var creatorPoint2 =
        new CreatorAffiliationPoints(
            null, nviOrganization1.hasPart().get(1).id(), asBigDecimal("0.25"));
    var creatorPoint3 =
        new CreatorAffiliationPoints(
            null, nviOrganization1.hasPart().get(0).id(), asBigDecimal("0.5"));
    return List.of(creatorPoint1, creatorPoint2, creatorPoint3);
  }

  private static Stream<PointParameters>
      twoCreatorsAffiliatedWithTwoDifferentInstitutionsPointProvider() {
    return Stream.of(
        // example from cristin calculations, publicationYear 2022
        new PointParameters(
            "AcademicArticle",
            "Journal",
            "LevelOne",
            false,
            2,
            asBigDecimal("0.7071"),
            asBigDecimal("0.7071"),
            asBigDecimal("1.4142")));
  }

  private static Stream<PointParameters> twoCreatorsAffiliatedWithOneInstitutionPointProvider() {
    return Stream.of(
        new PointParameters(
            "AcademicCommentary",
            "Series",
            "LevelOne",
            false,
            3,
            asBigDecimal("4.0825"),
            asBigDecimal("2.8868"),
            asBigDecimal("6.9693")),
        new PointParameters(
            "AcademicCommentary",
            "Series",
            "LevelTwo",
            true,
            4,
            asBigDecimal("7.3539"),
            asBigDecimal("5.2000"),
            asBigDecimal("12.5539")),
        new PointParameters(
            "AcademicCommentary",
            "Series",
            "LevelTwo",
            false,
            4,
            asBigDecimal("5.6569"),
            asBigDecimal("4.0000"),
            asBigDecimal("9.6569")),
        new PointParameters(
            "AcademicMonograph",
            "Series",
            "LevelOne",
            false,
            3,
            asBigDecimal("4.0825"),
            asBigDecimal("2.8868"),
            asBigDecimal("6.9693")),
        new PointParameters(
            "AcademicMonograph",
            "Series",
            "LevelTwo",
            true,
            4,
            asBigDecimal("7.3539"),
            asBigDecimal("5.2000"),
            asBigDecimal("12.5539")),
        new PointParameters(
            "AcademicMonograph",
            "Series",
            "LevelTwo",
            false,
            4,
            asBigDecimal("5.6569"),
            asBigDecimal("4.0000"),
            asBigDecimal("9.6569")),
        new PointParameters(
            "AcademicChapter",
            "Series",
            "LevelOne",
            true,
            5,
            asBigDecimal("0.8222"),
            asBigDecimal("0.5814"),
            asBigDecimal("1.4036")),
        new PointParameters(
            "AcademicChapter",
            "Series",
            "LevelTwo",
            false,
            5,
            asBigDecimal("1.8974"),
            asBigDecimal("1.3416"),
            asBigDecimal("3.2390")),
        new PointParameters(
            "AcademicArticle",
            "Journal",
            "LevelOne",
            false,
            7,
            asBigDecimal("0.5345"),
            asBigDecimal("0.3780"),
            asBigDecimal("0.9125")),
        new PointParameters(
            "AcademicArticle",
            "Journal",
            "LevelTwo",
            false,
            7,
            asBigDecimal("1.6036"),
            asBigDecimal("1.1339"),
            asBigDecimal("2.7375")),
        new PointParameters(
            "AcademicLiteratureReview",
            "Journal",
            "LevelOne",
            true,
            8,
            asBigDecimal("0.6500"),
            asBigDecimal("0.4596"),
            asBigDecimal("1.1096")),
        new PointParameters(
            "AcademicLiteratureReview",
            "Journal",
            "LevelTwo",
            true,
            8,
            asBigDecimal("1.9500"),
            asBigDecimal("1.3789"),
            asBigDecimal("3.3289")));
  }

  private static Stream<PointParameters> singleCreatorSingleInstitutionPointProvider() {
    return Stream.of(
        new PointParameters(
            "AcademicCommentary",
            "Series",
            "LevelOne",
            false,
            1,
            asBigDecimal("5"),
            null,
            asBigDecimal("5")),
        new PointParameters(
            "AcademicCommentary",
            "Series",
            "LevelTwo",
            false,
            1,
            asBigDecimal("8"),
            null,
            asBigDecimal("8")),
        new PointParameters(
            "AcademicCommentary",
            "Publisher",
            "LevelOne",
            false,
            1,
            asBigDecimal("5"),
            null,
            asBigDecimal("5")),
        new PointParameters(
            "AcademicCommentary",
            "Publisher",
            "LevelTwo",
            false,
            1,
            asBigDecimal("8"),
            null,
            asBigDecimal("8")),
        new PointParameters(
            "AcademicMonograph",
            "Series",
            "LevelOne",
            false,
            1,
            asBigDecimal("5"),
            null,
            asBigDecimal("5")),
        new PointParameters(
            "AcademicMonograph",
            "Series",
            "LevelTwo",
            false,
            1,
            asBigDecimal("8"),
            null,
            asBigDecimal("8")),
        new PointParameters(
            "AcademicMonograph",
            "Publisher",
            "LevelOne",
            false,
            1,
            asBigDecimal("5"),
            null,
            asBigDecimal("5")),
        new PointParameters(
            "AcademicMonograph",
            "Publisher",
            "LevelTwo",
            false,
            1,
            asBigDecimal("8"),
            null,
            asBigDecimal("8")),
        new PointParameters(
            "AcademicChapter",
            "Series",
            "LevelOne",
            false,
            1,
            asBigDecimal("1"),
            null,
            asBigDecimal("1")),
        new PointParameters(
            "AcademicChapter",
            "Series",
            "LevelTwo",
            false,
            1,
            asBigDecimal("3"),
            null,
            asBigDecimal("3")),
        new PointParameters(
            "AcademicChapter",
            "Publisher",
            "LevelOne",
            false,
            1,
            asBigDecimal("0.7"),
            null,
            asBigDecimal("0.7")),
        new PointParameters(
            "AcademicChapter",
            "Publisher",
            "LevelTwo",
            false,
            1,
            asBigDecimal("1"),
            null,
            asBigDecimal("1")),
        new PointParameters(
            "AcademicArticle",
            "Journal",
            "LevelOne",
            false,
            1,
            asBigDecimal("1"),
            null,
            asBigDecimal("1")),
        new PointParameters(
            "AcademicArticle",
            "Journal",
            "LevelTwo",
            false,
            1,
            asBigDecimal("3"),
            null,
            asBigDecimal("3")),
        new PointParameters(
            "AcademicLiteratureReview",
            "Journal",
            "LevelOne",
            false,
            1,
            asBigDecimal("1"),
            null,
            asBigDecimal("1")),
        new PointParameters(
            "AcademicLiteratureReview",
            "Journal",
            "LevelTwo",
            false,
            1,
            asBigDecimal("3"),
            null,
            asBigDecimal("3")));
  }

  private static BigDecimal asBigDecimal(String val) {
    return new BigDecimal(val).setScale(4, RoundingMode.HALF_UP);
  }

  private record PointParameters(
      String instanceType,
      String channelType,
      String level,
      boolean isInternationalCollaboration,
      int creatorShareCount,
      BigDecimal institution1Points,
      BigDecimal institution2Points,
      BigDecimal totalPoints) {}

  private URI addPublicationToS3(SampleExpandedPublication publication) {
    try {
      return s3Driver.insertFile(
          UnixPath.of(publication.identifier().toString()), publication.toJsonString());
    } catch (IOException e) {
      throw new RuntimeException("Failed to add publication to S3", e);
    }
  }

  private CandidateEvaluatedMessage getMessageBody() {
    try {
      var sentMessages = queueClient.getSentMessages();
      var message = sentMessages.getFirst();
      return objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private NviCandidate getEvaluatedCandidate(SampleExpandedPublication publication) {
    var fileUri = addPublicationToS3(publication);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    return (NviCandidate) getMessageBody().candidate();
  }

  private void assertThatPointValuesMatch(PointParameters parameters, NviCandidate candidate) {
    assertEquals(parameters.totalPoints(), candidate.totalPoints());
    if (nonNull(parameters.institution2Points())) {
      assertThat(candidate.institutionPoints())
          .extracting(InstitutionPoints::institutionId, InstitutionPoints::institutionPoints)
          .containsExactlyInAnyOrder(
              tuple(nviOrganization1.id(), parameters.institution1Points()),
              tuple(nviOrganization2.id(), parameters.institution2Points()));
    } else {
      assertThat(candidate.institutionPoints())
          .extracting(InstitutionPoints::institutionId, InstitutionPoints::institutionPoints)
          .containsExactlyInAnyOrder(tuple(nviOrganization1.id(), parameters.institution1Points()));
    }
  }
}
