package no.sikt.nva.nvi.events.evaluator.model;

import java.util.Arrays;

// FIXME: Remove this class, it is a duplicate of ChannelType in nvi-commons
public enum PublicationChannel {
  JOURNAL("Journal"),
  SERIES("Series"),
  PUBLISHER("Publisher");

  private final String value;

  PublicationChannel(String value) {
    this.value = value;
  }

  public static PublicationChannel parse(String value) {
    return Arrays.stream(values())
        .filter(channelType -> channelType.getValue().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow();
  }

  public String getValue() {
    return value;
  }
}
