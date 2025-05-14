package no.sikt.nva.nvi.events.evaluator;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDate;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PointCalculationDto;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
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
    var publicationDate = randomPublicationDate();
    var year = publicationDate.year();
    setupOpenPeriod(scenario, year);
    factory =
        new SampleExpandedPublicationFactory(authorizedBackendUriRetriever, uriRetriever)
            .withPublicationDate(publicationDate);

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
            .withCreatorAffiliatedWith(nviOrganization1)
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
    var nonNviOrganization2 = factory.setupTopLevelOrganization(nonNviCreatorCountryCode, false);
    var publication =
        factory
            .withCreatorAffiliatedWith(nviOrganization1, nviOrganization2)
            .withCreatorAffiliatedWith(nviOrganization1)
            .withCreatorsAffiliatedWith(nonNviCreatorCount, nonNviOrganization2)
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
    var nonNviOrganization2 = factory.setupTopLevelOrganization(nonNviCreatorCountryCode, false);
    var publication =
        factory
            .withCreatorAffiliatedWith(nviOrganization1)
            .withCreatorAffiliatedWith(nviOrganization2)
            .withCreatorsAffiliatedWith(nonNviCreatorCount, nonNviOrganization2)
            .withPublicationType(parameters.instanceType())
            .withPublicationChannel(parameters.channelType(), parameters.level())
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    assertThatPointValuesMatch(parameters, candidate);
  }

  @Test
  void shouldCountTotalCreatorSharesForTopLevelAffiliations() {
    var publication =
        factory.withCreatorAffiliatedWith(nviOrganization1.hasPart()).getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication).pointCalculation();
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
            .withCreatorAffiliatedWith(nviOrganization1.hasPart())
            .withNonCreatorsAffiliatedWith(1, nonNviOrganization)
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication).pointCalculation();
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
            .withCreatorAffiliatedWith(nviOrganization1.hasPart())
            .withNonCreatorAffiliatedWith(nviOrganization2)
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication).pointCalculation();
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
            .withCreatorAffiliatedWith(nviOrganization1.hasPart())
            .withCreatorAffiliatedWith()
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication).pointCalculation();
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
            .withCreatorAffiliatedWith(
                nviOrganization1, organizationWithoutId1, organizationWithoutId2)
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication).pointCalculation();

    var expectedPoints = asBigDecimal("1");
    assertThat(candidate)
        .hasFieldOrPropertyWithValue("totalPoints", expectedPoints)
        .hasFieldOrPropertyWithValue("creatorShareCount", 1)
        .extracting(PointCalculationDto::institutionPoints)
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
            .withCreatorAffiliatedWith(nviOrganization1)
            .withCreatorAffiliatedWith(organizationWithoutId1, organizationWithoutId2)
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication).pointCalculation();
    assertThat(candidate)
        .hasFieldOrPropertyWithValue("totalPoints", asBigDecimal("0.7071"))
        .hasFieldOrPropertyWithValue("creatorShareCount", 2)
        .extracting(PointCalculationDto::institutionPoints)
        .extracting(List::getFirst)
        .hasFieldOrPropertyWithValue("institutionPoints", asBigDecimal("0.7071"));
  }

  @Test
  void shouldCountOneInstitutionShareForCreatorsWithSeveralAffiliationsInSameInstitution() {
    var expectedPoints = getExpectedPointsForSingleContributorWithTwoAffiliations();
    var publication =
        factory.withCreatorAffiliatedWith(nviOrganization1.hasPart()).getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication).pointCalculation();
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
            .withCreatorAffiliatedWith(nviOrganization1.hasPart())
            .withCreatorAffiliatedWith(nviOrganization1.hasPart().getFirst())
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication).pointCalculation();
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

  private void assertThatPointValuesMatch(
      PointParameters parameters, UpsertNviCandidateRequest candidate) {
    var actualPointCalculation = candidate.pointCalculation();
    assertEquals(parameters.totalPoints(), actualPointCalculation.totalPoints());
    if (nonNull(parameters.institution2Points())) {
      assertThat(actualPointCalculation.institutionPoints())
          .extracting(InstitutionPoints::institutionId, InstitutionPoints::institutionPoints)
          .containsExactlyInAnyOrder(
              tuple(nviOrganization1.id(), parameters.institution1Points()),
              tuple(nviOrganization2.id(), parameters.institution2Points()));
    } else {
      assertThat(actualPointCalculation.institutionPoints())
          .extracting(InstitutionPoints::institutionId, InstitutionPoints::institutionPoints)
          .containsExactlyInAnyOrder(tuple(nviOrganization1.id(), parameters.institution1Points()));
    }
  }
}
