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
  private static final Instant previousMonth = ZonedDateTime.now().minusMonths(1).toInstant();
  private static final Instant previousYear = ZonedDateTime.now().minusMonths(12).toInstant();
  private static final Instant nextMonth = ZonedDateTime.now().plusMonths(1).toInstant();
  private static final Instant nextYear = ZonedDateTime.now().plusMonths(12).toInstant();
  private final ScenarioContext scenarioContext;
  private final NviPeriodService periodService;

  public ReportingPeriodSteps(ScenarioContext scenarioContext) {
    this.scenarioContext = scenarioContext;
    this.periodService = new NviPeriodService(ENVIRONMENT, scenarioContext.getPeriodRepository());
  }

  @Given("a closed period for year {string}")
  public void givenClosedPeriod(String year) {
    upsertPeriod(year, previousYear, previousMonth, scenarioContext.getPeriodRepository());
  }

  @Given("an open period for year {string}")
  public void givenOpenPeriod(String year) {
    upsertPeriod(year, previousMonth, nextYear, scenarioContext.getPeriodRepository());
  }

  @Given("a future period for year {string}")
  public void givenFuturePeriod(String year) {
    upsertPeriod(year, nextMonth, nextYear, scenarioContext.getPeriodRepository());
  }

  @When("the period for year {string} is updated with a reporting date in the past")
  public void whenPeriodIsMovedToPast(String year) {
    updatePeriod(year, previousYear, previousMonth, scenarioContext.getPeriodRepository());
  }

  @When("the period for year {string} is updated with a reporting date in the future")
  public void whenPeriodIsMovedToFuture(String year) {
    updatePeriod(year, previousMonth, nextYear, scenarioContext.getPeriodRepository());
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
