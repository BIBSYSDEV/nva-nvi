package cucumber.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cucumber.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class CountingSteps {

  private final ScenarioContext scenarioContext;

  public CountingSteps(ScenarioContext scenarioContext) {
    this.scenarioContext = scenarioContext;
  }

  @Given("that we start counting at {int}")
  public void startCounting(int number) {
    scenarioContext.setPotato(number);
  }

  @When("we add {int}")
  public void increment(int number) {
    int potato = scenarioContext.getPotato();
    scenarioContext.setPotato(potato + number);
  }

  @Then("we should have counted to {int}")
  public void countEquals(int number) {
    int potato = scenarioContext.getPotato();
    assertEquals(number, potato);
  }
}
