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
import java.net.URI;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class EvaluationSteps {
  private static final String OPEN_PERIOD = "OPEN";
  private static final String CLOSED_PERIOD = "CLOSED";
  private static final String FUTURE_PERIOD = "PENDING";
  private final TestScenario scenario;
  private final EvaluationContext evaluationContext;
  private SampleExpandedPublicationFactory publicationBuilder;
  private final URI publicationId;

  public EvaluationSteps(TestScenario scenario) {
    this.scenario = scenario;
    this.publicationBuilder = new SampleExpandedPublicationFactory(scenario);
    this.publicationId = publicationBuilder.getExpandedPublication().id();
    this.evaluationContext = new EvaluationContext(scenario);
  }

  @Given("an unreported Candidate")
  public void givenAnUnreportedCandidate() {
    givenAnApplicablePublication();
    givenTheReportingPeriodForThePublicationIs("OPEN");
    whenThePublicationIsEvaluated();

    assertPublicationIsUnreportedCandidate();
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

  @When("the Publication is updated to be non-applicable")
  public void whenThePublicationTypeIsChangedSoThatThePublicationIsNoLongerApplicable2() {
    evaluationContext.evaluatePublicationAndPersistResult(
        publicationBuilder.withPublicationType("ComicBook").getExpandedPublication());
  }

  @Then("it becomes a Candidate")
  public void thePublicationBecomesACandidate() {
    assertPublicationIsUnreportedCandidate();
  }

  @Then("it becomes a NonCandidate")
  public void thenThePublicationBecomesANonCandidate() {
    assertPublicationIsNonCandidate();
    assertCandidateIsUpdated();
  }

  @Then("the Candidate is updated")
  public void thenTheCandidateIsUpdated() {
    assertPublicationIsUnreportedCandidate();
    assertCandidateIsUpdated();
  }

  private void assertPublicationIsUnreportedCandidate() {
    var candidate = getCandidateByPublicationId();
    assertThat(candidate)
        .extracting(Candidate::getPublicationId, Candidate::isApplicable, Candidate::isReported)
        .containsExactly(publicationId, true, false);
    assertThat(candidate.approvals()).isNotEmpty();
  }

  private void assertPublicationIsNonCandidate() {
    var candidate = getCandidateByPublicationId();
    assertThat(candidate)
        .extracting(Candidate::getPublicationId, Candidate::isApplicable, Candidate::isReported)
        .containsExactly(publicationId, false, false);
    assertThat(candidate.approvals()).isEmpty();
  }

  private void assertCandidateIsUpdated() {
    var candidate = getCandidateByPublicationId();
    var evaluationTimestamp = evaluationContext.getLastEvaluationTimestamp();

    assertThat(candidate.createdDate()).isBefore(evaluationTimestamp);
    assertThat(candidate.modifiedDate()).isAfterOrEqualTo(evaluationTimestamp);
  }

  private Candidate getCandidateByPublicationId() {
    var publication = publicationBuilder.getExpandedPublication();
    return scenario.getCandidateByPublicationId(publication.id());
  }
}
