package cucumber.steps;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
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
    if (CLOSED_PERIOD.equals(periodState)) {
      setupClosedPeriod(scenario, publicationDate.year());
    } else if (OPEN_PERIOD.equals(periodState)) {
      setupOpenPeriod(scenario, publicationDate.year());
    } else {
      throw new IllegalArgumentException("Invalid period state: " + periodState);
    }
  }

  @When("the Publication is evaluated")
  public void whenThePublicationIsEvaluated() {
    evaluationContext.evaluatePublicationAndPersistResult(
        publicationBuilder.getExpandedPublication());
  }

  @Then("the Publication is a Candidate")
  public void thenThePublicationIsACandidate() {
    var publication = publicationBuilder.getExpandedPublication();
    var candidate = getCandidateByPublicationId(publication);
    assertThat(candidate)
        .extracting(Candidate::getPublicationId, Candidate::isApplicable)
        .containsExactly(publication.id(), true);

    assertThat(candidate.approvals()).isNotEmpty();
  }

  @Given("the Publication type is changed so that the Publication is no longer applicable")
  public void whenThePublicationTypeIsChangedSoThatThePublicationIsNoLongerApplicable() {
    evaluationContext.evaluatePublicationAndPersistResult(
        publicationBuilder.withPublicationType("ComicBook").getExpandedPublication());
  }

  @Then("the Publication is a NonCandidate")
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
