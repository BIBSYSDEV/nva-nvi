package cucumber.steps;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getGlobalEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.updatePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.upsertPeriod;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cucumber.contexts.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import nva.commons.core.Environment;

public class ReportingPeriodSteps {

  private static final Environment ENVIRONMENT = getGlobalEnvironment();
  private static final Instant PREVIOUS_MONTH = ZonedDateTime.now().minusMonths(1).toInstant();
  private static final Instant PREVIOUS_YEAR = ZonedDateTime.now().minusMonths(12).toInstant();
  private static final Instant NEXT_MONTH = ZonedDateTime.now().plusMonths(1).toInstant();
  private static final Instant NEXT_YEAR = ZonedDateTime.now().plusMonths(12).toInstant();
  private final ScenarioContext scenarioContext;
  private final NviPeriodService periodService;

  public ReportingPeriodSteps(ScenarioContext scenarioContext) {
    this.scenarioContext = scenarioContext;
    this.periodService = new NviPeriodService(ENVIRONMENT, scenarioContext.getPeriodRepository());
  }

  @Given("a closed period for year {string}")
  public void givenClosedPeriod(String year) {
    upsertPeriod(year, PREVIOUS_YEAR, PREVIOUS_MONTH, scenarioContext.getPeriodRepository());
  }

  @Given("an open period for year {string}")
  public void givenOpenPeriod(String year) {
    upsertPeriod(year, PREVIOUS_MONTH, NEXT_YEAR, scenarioContext.getPeriodRepository());
  }

  @Given("a future period for year {string}")
  public void givenFuturePeriod(String year) {
    upsertPeriod(year, NEXT_MONTH, NEXT_YEAR, scenarioContext.getPeriodRepository());
  }

  @When("the period for year {string} is updated with a reporting date in the past")
  public void whenPeriodIsMovedToPast(String year) {
    updatePeriod(year, PREVIOUS_YEAR, PREVIOUS_MONTH, scenarioContext.getPeriodRepository());
  }

  @When("the period for year {string} is updated with a reporting date in the future")
  public void whenPeriodIsMovedToFuture(String year) {
    updatePeriod(year, PREVIOUS_MONTH, NEXT_YEAR, scenarioContext.getPeriodRepository());
  }

  @Then("the period for {string} should be closed")
  public void thenPeriodIsClosed(String year) {
    var period = periodService.getByPublishingYear(year);
    assertTrue(period.isClosed());
  }

  @Then("the period for {string} should not be closed")
  public void thenPeriodIsNotClosed(String year) {
    var period = periodService.getByPublishingYear(year);
    assertFalse(period.isClosed());
  }
}
