package no.sikt.nva.nvi.test;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static no.sikt.nva.nvi.test.TestConstants.IDENTIFIER_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.NAME_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PRINT_ISSN_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PUBLISHER_TYPE;
import static no.sikt.nva.nvi.test.TestConstants.SCIENTIFIC_VALUE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.YEAR_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.createNodeWithType;
import static no.sikt.nva.nvi.test.TestUtils.publicationChannelId;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotBlank;
import static no.unit.nva.testutils.RandomDataGenerator.randomIssn;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;

public record SampleExpandedPublicationChannel(
    String type,
    URI id,
    String identifier,
    String name,
    String level,
    String printIssn,
    String year) {

  public static Builder builder() {
    return new Builder();
  }

  /** Copies every field of an existing channel, so a modified copy keeps the rest intact. */
  public static Builder from(SampleExpandedPublicationChannel channel) {
    var builder =
        new Builder()
            .withType(channel.type())
            .withIdentifier(channel.identifier())
            .withName(channel.name())
            .withLevel(channel.level())
            .withPrintIssn(channel.printIssn())
            .withYear(channel.year());
    if (isNull(channel.id())) {
      return builder.withoutId();
    }
    return builder.isDerivedId(channel.id()) ? builder : builder.withId(channel.id());
  }

  public ObjectNode asObjectNode() {
    var node = createNodeWithType(type);
    TestUtils.putIfNotNull(node, ID_FIELD, id);
    putIfNotBlank(node, IDENTIFIER_FIELD, identifier);
    putIfNotBlank(node, NAME_FIELD, name);
    putIfNotBlank(node, SCIENTIFIC_VALUE_FIELD, level);
    putIfNotBlank(node, PRINT_ISSN_FIELD, printIssn);
    putIfNotBlank(node, YEAR_FIELD, year);
    return node;
  }

  public static final class Builder {

    private String type;
    private URI id;
    private boolean deriveIdWhenMissing = true;
    private String identifier = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    private String name = randomString();
    private String level = "Unassigned";
    private String printIssn;
    private boolean printIssnSet;
    private String year = THIS_YEAR;

    private Builder() {}

    public Builder withType(String type) {
      this.type = type;
      return this;
    }

    /** Sets an explicit ID. A null value means "not set", so the ID is derived on build. */
    public Builder withId(URI id) {
      this.id = id;
      this.deriveIdWhenMissing = true;
      return this;
    }

    /** Produces a channel without an ID, for tests of channels that cannot be resolved. */
    public Builder withoutId() {
      withId(null);
      this.deriveIdWhenMissing = false;
      return this;
    }

    public Builder withIdentifier(String identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder withYear(String year) {
      this.year = year;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withPrintIssn(String printIssn) {
      this.printIssn = printIssn;
      this.printIssnSet = true;
      return this;
    }

    public Builder withLevel(String level) {
      this.level = level;
      return this;
    }

    /**
     * The ID is derived from the type, identifier and year unless set explicitly. Only serial
     * publications get a random print ISSN by default, since publishers never have one.
     */
    public SampleExpandedPublicationChannel build() {
      var resolvedId = isNull(id) && deriveIdWhenMissing ? deriveId() : id;
      var resolvedPrintIssn = printIssnSet ? printIssn : defaultPrintIssn();
      return new SampleExpandedPublicationChannel(
          type, resolvedId, identifier, name, level, resolvedPrintIssn, year);
    }

    private URI deriveId() {
      requireNonNull(type, "Channel type is required to derive the channel ID");
      requireNonNull(identifier, "Channel identifier is required to derive the channel ID");
      requireNonNull(year, "Channel year is required to derive the channel ID");
      return publicationChannelId(type, identifier, year);
    }

    private boolean isDerivedId(URI candidate) {
      return nonNull(type)
          && nonNull(identifier)
          && nonNull(year)
          && publicationChannelId(type, identifier, year).equals(candidate);
    }

    private String defaultPrintIssn() {
      return PUBLISHER_TYPE.equals(type) ? null : randomIssn();
    }
  }
}
