package cucumber.steps;


import cucumber.ScenarioContext;
import io.cucumber.java.en.When;

public class MultiplicationSteps {

  private final ScenarioContext counter;

  public MultiplicationSteps(ScenarioContext counter) {
    this.counter = counter;
  }

  @When("we double the number")
  public void doubleIt() {
    var newCount = counter.getCount() * 2;
    counter.setCount(newCount);
  }
}
