package cucumber.steps;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.updatePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.upsertPeriod;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cucumber.contexts.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.service.model.NviPeriod;

public class ReportingPeriodSteps {
  private static final Instant previousMonth = ZonedDateTime.now().minusMonths(1).toInstant();
  private static final Instant previousYear = ZonedDateTime.now().minusMonths(12).toInstant();
  private static final Instant nextMonth = ZonedDateTime.now().plusMonths(1).toInstant();
  private static final Instant nextYear = ZonedDateTime.now().plusMonths(12).toInstant();
  private final ScenarioContext scenarioContext;

  public ReportingPeriodSteps(ScenarioContext scenarioContext) {
    this.scenarioContext = scenarioContext;
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
    var period = NviPeriod.fetchByPublishingYear(year, scenarioContext.getPeriodRepository());
    assertTrue(period.isClosed());
  }

  @Then("the period for {string} should not be closed")
  public void thenPeriodIsNotClosed(String year) {
    var period = NviPeriod.fetchByPublishingYear(year, scenarioContext.getPeriodRepository());
    assertFalse(period.isClosed());
  }
}
