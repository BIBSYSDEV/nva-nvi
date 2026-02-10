package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateInCurrentYear;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;

import java.net.URI;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.client.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NviCandidateEvaluationInPeriodTest extends EvaluationTest {

  private SampleExpandedPublicationFactory factory;
  private Organization nviOrganization;
  private URI publicationId;

  @BeforeEach
  void setup() {
    factory = new SampleExpandedPublicationFactory();
    publicationId = factory.getPublicationId();
    nviOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    mockGetAllCustomersResponse(factory.getCustomerOrganizations());
  }

  @Test
  void shouldEvaluateAsCandidateWhenPeriodExistsAndIsNotClosed() {
    setupFuturePeriod(scenario, CURRENT_YEAR);
    var publication = getValidNviCandidateForCurrentYear();

    handleEvaluation(publication);

    assertThatPublicationIsValidCandidate(publicationId);
  }

  @Test
  void shouldEvaluateAsCandidateWhenPeriodExistsAndIsStartedAndIsNotClosed() {
    setupOpenPeriod(scenario, CURRENT_YEAR);
    var publication = getValidNviCandidateForCurrentYear();

    handleEvaluation(publication);

    assertThatPublicationIsValidCandidate(publicationId);
  }

  @Test
  void shouldNotEvaluateAsCandidateWhenPeriodExistsAndIsClosed() {
    setupClosedPeriod(scenario, CURRENT_YEAR);
    var publication = getValidNviCandidateForCurrentYear();

    handleEvaluation(publication);

    assertThatNoCandidateExistsForPublication(publicationId);
  }

  @Test
  void shouldNotEvaluateAsCandidateWhenPeriodDoesNotExist() {
    var publication = getValidNviCandidateForCurrentYear();

    handleEvaluation(publication);

    assertThatNoCandidateExistsForPublication(publicationId);
  }

  private String getValidNviCandidateForCurrentYear() {
    return factory
        .withCreatorAffiliatedWith(nviOrganization)
        .withPublicationDate(randomPublicationDateInCurrentYear())
        .getExpandedPublicationBuilder()
        .build()
        .toJsonString();
  }
}
