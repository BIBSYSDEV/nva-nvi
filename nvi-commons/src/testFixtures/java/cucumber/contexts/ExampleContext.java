package cucumber.contexts;

import no.sikt.nva.nvi.common.service.CountingService;

public class ExampleContext {

  private final CountingService exampleCounter;

  public ExampleContext(CountingService exampleCounter) {
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
