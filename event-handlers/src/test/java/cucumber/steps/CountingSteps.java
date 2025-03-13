package cucumber.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cucumber.contexts.ExampleContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class CountingSteps {

  private final ExampleContext counter;

  public CountingSteps(ExampleContext counter) {
    this.counter = counter;
  }

  @Given("that we start counting at {int}")
  public void startCounting(int number) {
    counter.setCount(number);
  }

  @When("we add {int}")
  public void addSomething(int number) {
    counter.addCount(number);
  }

  @Then("we should have counted to {int}")
  public void countEquals(int number) {
    assertEquals(number, counter.getCount());
  }
}
