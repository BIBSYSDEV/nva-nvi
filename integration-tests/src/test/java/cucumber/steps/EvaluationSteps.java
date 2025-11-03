package cucumber.steps;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.unverifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.verifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDate;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static org.assertj.core.api.Assertions.assertThat;

import cucumber.contexts.EvaluationContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.test.SampleExpandedPublication;

public class EvaluationSteps {
  private static final String OPEN_PERIOD = "OPEN";
  private static final String CLOSED_PERIOD = "CLOSED";
  private static final String FUTURE_PERIOD = "PENDING";
  private final TestScenario scenario;
  private final EvaluationContext evaluationContext;
  private SampleExpandedPublicationFactory publicationBuilder;

  public EvaluationSteps(TestScenario scenario) {
    this.scenario = scenario;
    this.publicationBuilder = new SampleExpandedPublicationFactory(scenario);
    this.evaluationContext = new EvaluationContext(scenario);
  }

  @Given("a Publication that has previously been evaluated as a Candidate")
  public void givenAPublicationThatHasPreviouslyBeenEvaluatedAsACandidate() {
    givenAnApplicablePublication();
    givenTheReportingPeriodForThePublicationIs("OPEN");
    whenThePublicationIsEvaluated();
    thenThePublicationIsACandidate();
  }

  @Given("an applicable Publication")
  public void givenAnApplicablePublication() {
    var publicationDate = randomPublicationDate();
    var nviOrganization = publicationBuilder.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    publicationBuilder =
        publicationBuilder
            .withPublicationDate(publicationDate)
            .withContributor(verifiedCreatorFrom(nviOrganization))
            .withContributor(unverifiedCreatorFrom(nviOrganization));
  }

  @Given("the reporting period for the Publication is {string}")
  public void givenTheReportingPeriodForThePublicationIs(String periodState) {
    var publicationDate = publicationBuilder.getExpandedPublication().publicationDate();
    switch (periodState) {
      case CLOSED_PERIOD -> setupClosedPeriod(scenario, publicationDate.year());
      case OPEN_PERIOD -> setupOpenPeriod(scenario, publicationDate.year());
      case FUTURE_PERIOD -> setupFuturePeriod(scenario, publicationDate.year());
      case null, default ->
          throw new IllegalArgumentException("Invalid period state: " + periodState);
    }
  }

  @When("the Publication is evaluated")
  public void whenThePublicationIsEvaluated() {
    evaluationContext.evaluatePublicationAndPersistResult(
        publicationBuilder.getExpandedPublication());
  }

  @Given("the Candidate is not reported")
  public void theCandidateIsNotReported() {
    // This is the default state, so we simply check it for now
    var publication = publicationBuilder.getExpandedPublication();
    var candidate = getCandidateByPublicationId(publication);
    assertThat(candidate.isReported()).isFalse();
  }

  @Then("the Publication is persisted as a Candidate")
  public void thenThePublicationIsACandidate() {
    var publication = publicationBuilder.getExpandedPublication();
    var candidate = getCandidateByPublicationId(publication);
    assertThat(candidate)
        .extracting(Candidate::getPublicationId, Candidate::isApplicable)
        .containsExactly(publication.id(), true);

    assertThat(candidate.approvals()).isNotEmpty();
  }

  @Then("the persisted data is updated")
  public void thenThePersistedCandidateIsUpdated() {
    var publication = publicationBuilder.getExpandedPublication();
    var candidate = getCandidateByPublicationId(publication);
    var evaluationTimestamp = evaluationContext.getLastEvaluationTimestamp();

    assertThat(candidate.createdDate()).isBefore(evaluationTimestamp);
    assertThat(candidate.modifiedDate()).isAfterOrEqualTo(evaluationTimestamp);
  }

  @Given("the Publication type is changed so that the Publication is no longer applicable")
  public void whenThePublicationTypeIsChangedSoThatThePublicationIsNoLongerApplicable() {
    evaluationContext.evaluatePublicationAndPersistResult(
        publicationBuilder.withPublicationType("ComicBook").getExpandedPublication());
  }

  @Then("the Publication is persisted as a NonCandidate")
  public void thenThePublicationIsANonCandidate() {
    var publication = publicationBuilder.getExpandedPublication();
    var candidate = getCandidateByPublicationId(publication);
    assertThat(candidate)
        .extracting(Candidate::getPublicationId, Candidate::isApplicable)
        .containsExactly(publication.id(), false);
    assertThat(candidate.approvals()).isEmpty();
  }

  private Candidate getCandidateByPublicationId(SampleExpandedPublication publication) {
    return scenario.getCandidateByPublicationId(publication.id());
  }
}
