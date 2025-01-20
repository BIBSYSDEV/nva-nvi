package no.sikt.nva.nvi.events.batch;

public record RequeueDlqInput(Integer count) {
  public static final int DEFAULT_COUNT = 10;

  public RequeueDlqInput() {
    this(null);
  }

  @Override
  public Integer count() {
    return count == null ? DEFAULT_COUNT : count;
  }
}
