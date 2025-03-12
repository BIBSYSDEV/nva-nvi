package cucumber.contexts;

import no.sikt.nva.nvi.common.service.CountingService;

public class ScenarioContext {

  private final CountingService exampleCounter;

  public ScenarioContext(CountingService exampleCounter) {
    this.exampleCounter = exampleCounter;
  }

  public void addCount(int count) {
    exampleCounter.addCount(count);
  }

  public void setCount(int count) {
    exampleCounter.setCount(count);
  }

  public int getCount() {
    return exampleCounter.getCount();
  }
}
