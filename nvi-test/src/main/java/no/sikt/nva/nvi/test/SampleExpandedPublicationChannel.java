package no.sikt.nva.nvi.test;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.IDENTIFIER_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.NAME_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PRINT_ISSN_FIELD;
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
    private String identifier = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    private String name = randomString();
    private String level = "Unassigned";
    private String printIssn = randomIssn();
    private String year = THIS_YEAR;

    private Builder() {}

    public Builder withType(String type) {
      this.type = type;
      return this;
    }

    public Builder withId(URI id) {
      this.id = id;
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
      return this;
    }

    public Builder withLevel(String level) {
      this.level = level;
      return this;
    }

    /** The ID is derived from the type, identifier and year unless set explicitly. */
    public SampleExpandedPublicationChannel build() {
      var resolvedId =
          isNull(id) && nonNull(type) ? publicationChannelId(type, identifier, year) : id;
      return new SampleExpandedPublicationChannel(
          type, resolvedId, identifier, name, level, printIssn, year);
    }
  }
}
