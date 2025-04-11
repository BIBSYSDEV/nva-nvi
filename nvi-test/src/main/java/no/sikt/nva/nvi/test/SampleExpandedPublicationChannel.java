package no.sikt.nva.nvi.test;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.NAME_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Locale;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public record SampleExpandedPublicationChannel(
    String type, URI id, String name, String level, Boolean valid, String printIssn) {

  public static Builder builder() {
    return new Builder();
  }

  public static Builder from(SampleExpandedPublicationChannel other) {
    return new Builder(other);
  }

  public ObjectNode asObjectNode() {
    var publicationContextNode = objectMapper.createObjectNode();
    publicationContextNode.put(TYPE_FIELD, type);
    publicationContextNode.put(ID_FIELD, id.toString());
    if (nonNull(name)) {
      publicationContextNode.put(NAME_FIELD, name);
    }
    if (nonNull(level)) {
      publicationContextNode.put("scientificValue", level);
    }
    if (nonNull(printIssn)) {
      publicationContextNode.put("printIssn", printIssn);
    }
    if (nonNull(valid)) {
      publicationContextNode.put("valid", valid);
    }
    return publicationContextNode;
  }

  @JacocoGenerated
  public static final class Builder {

    private String type;
    private URI id = randomUri();
    private String name = randomString();
    private String level = "Unassigned";
    private Boolean valid;
    private String printIssn;

    private Builder() {}

    private Builder(SampleExpandedPublicationChannel other) {
      this.type = other.type();
      this.id = other.id();
      this.name = other.name();
      this.level = other.level();
      this.valid = other.valid();
      this.printIssn = other.printIssn();
    }

    public Builder withType(String type) {
      this.type = type.toLowerCase(Locale.ROOT);
      return this;
    }

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withLevel(String level) {
      this.level = level;
      return this;
    }

    public SampleExpandedPublicationChannel build() {
      return new SampleExpandedPublicationChannel(type, id, name, level, valid, printIssn);
    }
  }
}
