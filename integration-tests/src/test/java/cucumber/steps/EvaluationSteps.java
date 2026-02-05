package cucumber.steps;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.unverifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.verifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateInYear;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cucumber.contexts.EvaluationContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.time.Instant;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
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

  @Given("an applicable Publication published {string} year")
  public void anApplicablePublicationPublishedYear(String relativeYear) {
    var publicationDate = randomPublicationDateInYear(mapRelativeYearToValue(relativeYear));
    setupApplicablePublication(publicationDate);
  }

  @Given("an unreported Candidate for the Publication exists")
  public void anUnreportedCandidateForThePublicationExists() {
    setupCandidate(publicationBuilder);

    var candidate = assertPublicationIsCandidate();
    assertThat(candidate.isReported()).isFalse();
  }

  @Given("a reported Candidate for the Publication exists")
  public void aReportedCandidateForThePublicationExists() {
    setupCandidate(publicationBuilder);
    var candidate = assertPublicationIsCandidate();
    setCandidateToReported(candidate);

    var updatedCandidate = assertPublicationIsCandidate();
    assertThat(updatedCandidate.isReported()).isTrue();
  }

  @Given("the reporting period for {string} year is {string}")
  public void theReportingPeriodForYearIs(String relativeYear, String periodState) {
    var publicationYear = mapRelativeYearToValue(relativeYear);
    switch (periodState) {
      case CLOSED_PERIOD -> setupClosedPeriod(scenario, publicationYear);
      case OPEN_PERIOD -> setupOpenPeriod(scenario, publicationYear);
      case FUTURE_PERIOD -> setupFuturePeriod(scenario, publicationYear);
      case null, default ->
          throw new IllegalArgumentException("Invalid period state: " + periodState);
    }
  }

  @Given("the reporting period for {string} year is undefined")
  public void theReportingPeriodForYearIsUndefined(String relativeYear) {
    var publicationYear = String.valueOf(mapRelativeYearToValue(relativeYear));
    var period = scenario.getPeriodService().findByPublishingYear(publicationYear);
    assertThat(period).isEmpty();
  }

  @When("the Publication is evaluated")
  public void whenThePublicationIsEvaluated() {
    evaluationContext.evaluatePublicationAndPersistResult(
        publicationBuilder.getExpandedPublication());
  }

  @When("the Publication date is changed to {string} year")
  public void thePublicationDateIsChangedToYear(String relativeYear) {
    var publicationDate = randomPublicationDateInYear(mapRelativeYearToValue(relativeYear));
    publicationBuilder = publicationBuilder.withPublicationDate(publicationDate);
    evaluationContext.evaluatePublicationAndPersistResult(
        publicationBuilder.getExpandedPublication());
  }

  @When("the Publication title is changed")
  public void thePublicationTitleIsChanged() {
    var updatedPublication =
        publicationBuilder
            .getExpandedPublicationBuilder()
            .withTitle(randomString())
            .withModifiedDate(Instant.now().toString())
            .build();
    evaluationContext.evaluatePublicationAndPersistResult(updatedPublication);
  }

  @When("the Publication is updated to be non-applicable")
  public void whenThePublicationIsUpdatedToBeNonApplicable() {
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

  @Then("it does not become a Candidate")
  public void thePublicationDoesNotBecomeACandidate() {
    assertThatThrownBy(this::getCandidateByPublicationId)
        .isInstanceOf(CandidateNotFoundException.class);
  }

  @Then("the Candidate is updated")
  public void thenTheCandidateIsUpdated() {
    assertCandidateIsUpdated();
    assertPublicationIsUnreportedCandidate();
  }

  @Then("the Candidate is not updated")
  public void theCandidateIsNotUpdated() {
    assertCandidateIsNotUpdated();
  }

  @Then("the Candidate is applicable")
  public void theCandidateIsApplicable() {
    var candidate = assertPublicationIsCandidate();
    assertThat(candidate.isApplicable()).isTrue();
  }

  @Then("the reporting period for the Candidate is {string} year")
  public void theReportingPeriodForTheCandidateIsYear(String relativeYear) {
    var candidate = assertPublicationIsCandidate();
    var year = mapRelativeYearToValue(relativeYear);
    var candidatePeriod = candidate.getPeriod().orElseThrow();
    assertThat(candidatePeriod.publishingYear()).isEqualTo(year);
  }

  private static int mapRelativeYearToValue(String yearName) {
    return switch (yearName) {
      case "previous", "last" -> CURRENT_YEAR - 1;
      case "this" -> CURRENT_YEAR;
      case "next" -> CURRENT_YEAR + 1;
      default -> throw new IllegalStateException("Unexpected value: " + yearName);
    };
  }

  private void setupApplicablePublication(PublicationDate publicationDate) {
    var nviOrganization = publicationBuilder.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    publicationBuilder =
        publicationBuilder
            .withPublicationDate(publicationDate)
            .withContributor(verifiedCreatorFrom(nviOrganization))
            .withContributor(unverifiedCreatorFrom(nviOrganization));
    evaluationContext.mockGetAllCustomersResponse(publicationBuilder.getCustomerOrganizations());
  }

  private Candidate assertPublicationIsCandidate() {
    var candidate = getCandidateByPublicationId();
    assertThat(candidate)
        .extracting(Candidate::getPublicationId, Candidate::isApplicable)
        .containsExactly(publicationId, true);
    assertThat(candidate.approvals()).isNotEmpty();
    assertThat(candidate.getPeriod()).isNotEmpty();
    return candidate;
  }

  private void assertPublicationIsUnreportedCandidate() {
    var candidate = assertPublicationIsCandidate();
    assertThat(candidate.isReported()).isFalse();
  }

  private void assertCandidateIsUpdated() {
    var candidate = getCandidateByPublicationId();
    var evaluationTimestamp = evaluationContext.getLastEvaluationTimestamp();

    assertThat(candidate.createdDate()).isBefore(evaluationTimestamp);
    assertThat(candidate.modifiedDate()).isAfterOrEqualTo(evaluationTimestamp);
  }

  private void assertCandidateIsNotUpdated() {
    var candidate = getCandidateByPublicationId();
    var evaluationTimestamp = evaluationContext.getLastEvaluationTimestamp();

    assertThat(candidate.modifiedDate()).isBefore(evaluationTimestamp);
  }

  private void assertPublicationIsNonCandidate() {
    var candidate = getCandidateByPublicationId();
    assertThat(candidate)
        .extracting(Candidate::getPublicationId, Candidate::isApplicable, Candidate::isReported)
        .containsExactly(publicationId, false, false);
    assertThat(candidate.approvals()).isEmpty();
  }

  private Candidate getCandidateByPublicationId() {
    var publication = publicationBuilder.getExpandedPublication();
    return scenario.getCandidateByPublicationId(publication.id());
  }

  private void setupCandidate(SampleExpandedPublicationFactory publicationFactory) {
    evaluationContext.mockGetAllCustomersResponse(publicationFactory.getCustomerOrganizations());
    var publication = publicationFactory.getExpandedPublication();
    var publicationYear = publication.publicationDate().year();
    var period =
        scenario
            .getPeriodService()
            .findByPublishingYear(publicationYear)
            .orElse(setupOpenPeriod(scenario, publicationYear));

    if (period.isOpen()) {
      evaluationContext.evaluatePublicationAndPersistResult(publication);
    } else {
      setupOpenPeriod(scenario, publicationYear);
      evaluationContext.evaluatePublicationAndPersistResult(publication);
      scenario.getPeriodRepository().update(period.toDao());
    }
  }

  private void setCandidateToReported(Candidate candidate) {
    var candidateDao = candidate.toDao();
    var dbCandidate = candidateDao.candidate().copy().reportStatus(ReportStatus.REPORTED).build();
    var updatedCandidateDao = candidateDao.copy().candidate(dbCandidate).build();

    scenario
        .getCandidateRepository()
        .updateCandidateAggregate(updatedCandidateDao, emptyList(), emptyList(), emptyList());
  }
}
