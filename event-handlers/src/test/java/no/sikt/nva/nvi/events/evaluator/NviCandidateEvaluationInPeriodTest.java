package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.SampleExpandedPublicationFactory.mapOrganizationToAffiliation;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDate;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.test.SampleExpandedContributor;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.sikt.nva.nvi.test.SampleExpandedPublicationDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NviCandidateEvaluationInPeriodTest extends EvaluationTest {

  private SampleExpandedPublicationFactory factory;

  @BeforeEach
  void setup() {
    var publicationDate = randomPublicationDate();
    factory = new SampleExpandedPublicationFactory(scenario).withPublicationDate(publicationDate);
  }

  @Test
  void shouldEvaluateAsCandidateWhenPeriodExistsAndIsNotClosed() {
    setupFuturePeriod(scenario, CURRENT_YEAR);

    var publication = getValidNviCandidateForCurrentYear();

    assertDoesNotThrow(() -> getEvaluatedCandidate(publication));
  }

  private SampleExpandedPublication getValidNviCandidateForCurrentYear() {
    return factory
        .withContributor(contributorAtNviOrganization())
        .getExpandedPublicationBuilder()
        .withPublicationDate(
            new SampleExpandedPublicationDate(String.valueOf(CURRENT_YEAR), null, null))
        .build();
  }

  private SampleExpandedContributor contributorAtNviOrganization() {
    return SampleExpandedContributor.builder()
        .withId(randomUri())
        .withVerificationStatus(List.of("Verified"))
        .withAffiliations(
            List.of(
                mapOrganizationToAffiliation(
                    factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true))))
        .withRole("Creator")
        .build();
  }
}
