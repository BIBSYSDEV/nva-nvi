package no.sikt.nva.nvi.events.evaluator.model;

public enum PublicationChannel {
  JOURNAL("Journal"),
  SERIES("Series"),
  PUBLISHER("Publisher");

  private final String value;

  PublicationChannel(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
