package cucumber;

public class ScenarioContext {

  private CountingService coolCounter;

  public ScenarioContext(CountingService coolCounter) {
    this.coolCounter = coolCounter;
  }

  public void addCount(int count) {
    coolCounter.addCount(count);
  }

  public void setCount(int count) {
    coolCounter.setCount(count);
  }

  public int getCount() {
    return coolCounter.getCount();
  }
}
