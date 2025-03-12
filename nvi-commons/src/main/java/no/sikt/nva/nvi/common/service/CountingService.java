package no.sikt.nva.nvi.common.service;

public class CountingService {
  private int count;

  public CountingService() {}

  public void addCount(int newCount) {
    count += newCount;
  }

  public void setCount(int newCount) {
    count = newCount;
  }

  public int getCount() {
    return count;
  }
}
