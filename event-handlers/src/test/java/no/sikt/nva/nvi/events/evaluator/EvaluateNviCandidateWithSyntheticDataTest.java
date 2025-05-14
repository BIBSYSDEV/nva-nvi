package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.verifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.PageCountFixtures.PAGE_NUMBER_AS_DTO;
import static no.sikt.nva.nvi.common.model.PageCountFixtures.PAGE_RANGE_AS_DTO;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDate;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PageCountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class EvaluateNviCandidateWithSyntheticDataTest extends EvaluationTest {
  private SampleExpandedPublicationFactory factory;
  private Organization nviOrganization;
  private Organization nonNviOrganization;

  @BeforeEach
  void setup() {
    var publicationDate = randomPublicationDate();
    setupOpenPeriod(scenario, publicationDate.year());
    factory = new SampleExpandedPublicationFactory(scenario).withPublicationDate(publicationDate);

    // Set up default organizations suitable for most test cases
    nviOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    nonNviOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_SWEDEN, false);
  }

  // The parser should be able to handle documents with 10 000 contributors in 30 seconds.
  // This test case is a bit more generous because the GitHub Actions test runner is underpowered.
  @ParameterizedTest
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  @ValueSource(ints = {100, 1_000, 5_000})
  void shouldParseDocumentWithManyContributorsWithinTimeOut(int numberOfForeignContributors) {
    var numberOfNorwegianContributors = 10;
    var publication =
        factory
            .withCreatorsAffiliatedWith(numberOfNorwegianContributors, nviOrganization)
            .withCreatorsAffiliatedWith(numberOfForeignContributors, nonNviOrganization)
            .getExpandedPublication();

    var expectedCreatorShares = numberOfNorwegianContributors + numberOfForeignContributors;
    var candidate = evaluatePublicationAndGetPersistedCandidate(publication);
    assertThat(candidate.getCreatorShareCount()).isEqualTo(expectedCreatorShares);
  }

  @Test
  void shouldPersistTopLevelOrganizations() {
    var nviOrganization2 = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    var publication =
        factory
            .withContributor(verifiedCreatorFrom(nviOrganization))
            .withContributor(verifiedCreatorFrom(nviOrganization2.hasPart().getFirst()))
            .getExpandedPublicationBuilder()
            .build();

    var candidate = evaluatePublicationAndGetPersistedCandidate(publication);
    var actualTopLevelOrganizations = candidate.getPublicationDetails().topLevelOrganizations();
    var expectedTopLevelOrganizations = List.of(nviOrganization, nviOrganization2);

    assertThat(actualTopLevelOrganizations)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedTopLevelOrganizations);
  }

  @Test
  void shouldPersistAbstractText() {
    var expectedAbstract = "Lorem ipsum";
    var publication =
        factory
            .withContributor(verifiedCreatorFrom(nviOrganization))
            .getExpandedPublicationBuilder()
            .withAbstract(expectedAbstract)
            .build();

    var candidate = getEvaluatedCandidate(publication);
    assertThat(candidate.publicationDetails().abstractText()).isEqualTo(expectedAbstract);
  }

  @ParameterizedTest
  @MethodSource("pageCountProvider")
  void shouldPersistPageCount(
      PageCountDto expectedPageCount, String publicationType, String channelType) {
    var publication =
        factory
            .withContributor(verifiedCreatorFrom(nviOrganization))
            .withPublicationChannel(channelType, "LevelOne")
            .getExpandedPublicationBuilder()
            .withAbstract("Lorem ipsum")
            .withInstanceType(publicationType)
            .withPageCount(
                expectedPageCount.first(), expectedPageCount.last(), expectedPageCount.total())
            .build();

    var candidate = getEvaluatedCandidate(publication);
    assertThat(candidate.publicationDetails().pageCount()).isEqualTo(expectedPageCount);
  }

  private static Stream<Arguments> pageCountProvider() {
    return Stream.of(
        argumentSet("Monograph with page count", PAGE_NUMBER_AS_DTO, "AcademicMonograph", "Series"),
        argumentSet("Article with page range", PAGE_RANGE_AS_DTO, "AcademicArticle", "Journal"));
  }
}
