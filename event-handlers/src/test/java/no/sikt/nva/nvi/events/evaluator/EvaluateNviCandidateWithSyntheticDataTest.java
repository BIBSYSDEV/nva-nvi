package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.SampleExpandedPublicationFactory.mapOrganizationToAffiliation;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.ROLE_CREATOR;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.unverifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.verifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.PageCountFixtures.PAGE_NUMBER_AS_DTO;
import static no.sikt.nva.nvi.common.model.PageCountFixtures.PAGE_RANGE_AS_DTO;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDate;
import static no.sikt.nva.nvi.test.TestConstants.ACADEMIC_ARTICLE;
import static no.sikt.nva.nvi.test.TestConstants.ACADEMIC_MONOGRAPH;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static no.sikt.nva.nvi.test.TestConstants.JOURNAL_TYPE;
import static no.sikt.nva.nvi.test.TestConstants.LEVEL_ONE;
import static no.sikt.nva.nvi.test.TestConstants.LEVEL_UNASSIGNED;
import static no.sikt.nva.nvi.test.TestConstants.PUBLISHER_TYPE;
import static no.sikt.nva.nvi.test.TestConstants.SERIES_TYPE;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PageCountDto;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.test.SampleExpandedContributor;
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

  // The parser must be able to handle documents with 10 000 contributors in 300 seconds.
  // This test case is a bit smaller to avoid slow builds in CI.
  @ParameterizedTest
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
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
    var actualTopLevelOrganizations = candidate.publicationDetails().topLevelOrganizations();
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
            .withPublicationChannel(channelType, LEVEL_ONE)
            .getExpandedPublicationBuilder()
            .withAbstract("Lorem ipsum")
            .withInstanceType(publicationType)
            .withPageCount(
                expectedPageCount.first(), expectedPageCount.last(), expectedPageCount.total())
            .build();

    var candidate = getEvaluatedCandidate(publication);
    assertThat(candidate.publicationDetails().pageCount()).isEqualTo(expectedPageCount);
  }

  @Test
  void shouldHandleContributorsWithMissingVerificationStatus() {
    var unverifiedCreator = unverifiedCreatorFrom(nviOrganization);
    var invalidCreator = createContributorWithoutVerificationStatus();
    var publication =
        factory
            .withContributor(unverifiedCreator)
            .withContributor(invalidCreator)
            .getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    assertThat(candidate.nviCreators())
        .hasSize(2)
        .allMatch(creator -> creator instanceof UnverifiedNviCreatorDto)
        .extracting(NviCreatorDto::name)
        .containsExactlyInAnyOrder(unverifiedCreator.name(), invalidCreator.names().getFirst());
  }

  private SampleExpandedContributor createContributorWithoutVerificationStatus() {
    var expandedAffiliations = List.of(mapOrganizationToAffiliation(nviOrganization));
    return SampleExpandedContributor.builder()
        .withId(randomUri())
        .withRole(ROLE_CREATOR.getValue())
        .withAffiliations(expandedAffiliations)
        .build();
  }

  @Test
  void shouldHandleDuplicateContributorWithMultipleRoles() {
    var expandedAffiliations = List.of(mapOrganizationToAffiliation(nviOrganization));
    var contributorBuilder =
        SampleExpandedContributor.builder()
            .withId(randomUri())
            .withVerificationStatus("Verified")
            .withAffiliations(expandedAffiliations);
    var creator = contributorBuilder.withRole("Creator").build();
    var nonCreator = contributorBuilder.withRole("NonCreator").build();

    var publication =
        factory.withContributor(creator).withContributor(nonCreator).getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    assertThat(candidate.publicationDetails().creatorCount()).isEqualTo(1);
    assertThat(candidate.nviCreators()).hasSize(1).extracting("id").containsExactly(creator.id());
  }

  @Test
  void shouldHandleDuplicateContributorWithMultipleVerificationStatuses() {
    var expandedAffiliations = List.of(mapOrganizationToAffiliation(nviOrganization));
    var contributorBuilder =
        SampleExpandedContributor.builder()
            .withId(randomUri())
            .withVerificationStatus(List.of("Verified", "NotVerified"))
            .withAffiliations(expandedAffiliations);
    var creator = contributorBuilder.withRole("Creator").build();
    var nonCreator = contributorBuilder.withRole("NonCreator").build();

    var publication =
        factory.withContributor(creator).withContributor(nonCreator).getExpandedPublication();

    var candidate = getEvaluatedCandidate(publication);
    assertThat(candidate.publicationDetails().creatorCount()).isEqualTo(1);
    assertThat(candidate.verifiedCreators())
        .hasSize(1)
        .extracting("id")
        .containsExactly(creator.id());
  }

  @Test
  void shouldThrowParsingExceptionWhenChannelIsMalformed() {
    var publication =
        factory
            .withPublicationChannel(JOURNAL_TYPE, LEVEL_ONE)
            .withPublicationChannel(JOURNAL_TYPE, null)
            .getExpandedPublicationBuilder()
            .build();

    var exception = assertThrows(RuntimeException.class, () -> getEvaluatedCandidate(publication));
    assertThat(exception.getMessage())
        .contains("ParsingException")
        .contains("Required field 'scientificValue' is null");
  }

  // TODO: Replace with test on revision filtering in NP-50261
  @Test
  void shouldIncludeRevisionFieldWithSingleChannel() {
    var publication =
        factory
            .withContributor(verifiedCreatorFrom(nviOrganization))
            .withPublicationChannel(JOURNAL_TYPE, LEVEL_ONE)
            .withRevisionStatus("Unrevised")
            .getExpandedPublication();

    var json = publication.toJsonString();
    assertThat(json).containsIgnoringWhitespaces("\"revision\": \"Unrevised\"");
  }

  // TODO: Replace with test on revision filtering in NP-50261
  @Test
  void shouldIncludeRevisionFieldForAnthology() {
    var publication =
        factory
            .withContributor(verifiedCreatorFrom(nviOrganization))
            .withPublicationChannel(PUBLISHER_TYPE, LEVEL_ONE)
            .withPublicationChannel(SERIES_TYPE, LEVEL_UNASSIGNED)
            .withRevisionStatus("Revised")
            .getExpandedPublication();

    var json = publication.toJsonString();
    assertThat(json).containsIgnoringWhitespaces("\"revision\": \"Revised\"");
  }

  private static Stream<Arguments> pageCountProvider() {
    return Stream.of(
        argumentSet(
            "Monograph with page count", PAGE_NUMBER_AS_DTO, ACADEMIC_MONOGRAPH, SERIES_TYPE),
        argumentSet("Article with page range", PAGE_RANGE_AS_DTO, ACADEMIC_ARTICLE, JOURNAL_TYPE));
  }
}
