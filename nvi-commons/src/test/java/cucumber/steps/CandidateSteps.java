package cucumber.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cucumber.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class CandidateSteps {

  private final ScenarioContext scenarioContext;

  public CandidateSteps(ScenarioContext scenarioContext) {
    this.scenarioContext = scenarioContext;
  }

  @Given("a publication that fulfills all criteria for NVI reporting")
  public void applicableCandidate() {}

  @Given("the publication is published in an open period")
  public void candidateInOpenPeriod() {}

  @When("the publication is evaluated")
  public void evaluateCandidate() {}

  @Given("the publication is named {string}")
  public void evaluateCandidate(String name) {
    scenarioContext.setExampleName(name);
  }

  @Then("it should be evaluated as a Candidate")
  public void evaluatedAsCandidate() {}

  @Then("the publication should have the title {string}")
  public void titleEquals(String expectedName) {
    var actualName = scenarioContext.getExampleName();
    assertEquals(actualName, expectedName);
  }
}
