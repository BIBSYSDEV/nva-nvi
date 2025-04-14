package no.sikt.nva.nvi.test;

import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.NAME_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.createNodeWithType;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotBlank;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;

public record SampleExpandedPublicationChannel(String type, URI id, String name, String level) {

  public static Builder builder() {
    return new Builder();
  }

  public ObjectNode asObjectNode() {
    var node = createNodeWithType(type);
    TestUtils.putIfNotNull(node, ID_FIELD, id);
    putIfNotBlank(node, NAME_FIELD, name);
    putIfNotBlank(node, "scientificValue", level);
    return node;
  }

  public static final class Builder {

    private String type;
    private URI id = randomUri();
    private final String name = randomString();
    private String level = "Unassigned";

    private Builder() {}

    public Builder withType(String type) {
      this.type = type;
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
      return new SampleExpandedPublicationChannel(type, id, name, level);
    }
  }
}
