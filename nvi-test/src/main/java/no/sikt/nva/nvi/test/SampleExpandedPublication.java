package no.sikt.nva.nvi.test;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.ABSTRACT_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ACADEMIC_ARTICLE;
import static no.sikt.nva.nvi.test.TestConstants.ACADEMIC_CHAPTER;
import static no.sikt.nva.nvi.test.TestConstants.ACADEMIC_LITERATURE_REVIEW;
import static no.sikt.nva.nvi.test.TestConstants.ACADEMIC_MONOGRAPH;
import static no.sikt.nva.nvi.test.TestConstants.BODY_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.CONTRIBUTORS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ENTITY_DESCRIPTION_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.IDENTIFIER_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.LANGUAGE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.MAIN_TITLE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ONE;
import static no.sikt.nva.nvi.test.TestConstants.PAGES_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PUBLICATION_CONTEXT_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PUBLICATION_DATE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PUBLICATION_INSTANCE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.REFERENCE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.STATUS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TOP_LEVEL_ORGANIZATIONS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;

/**
 * This is intended to create a JSON representation of an expanded resource, mirroring the expected
 * input documents from `nva-publication-api`.
 */
@JacocoGenerated
public record SampleExpandedPublication(
    URI id,
    UUID identifier,
    String mainTitle,
    String issn,
    String publicationContextType,
    List<SampleExpandedPublicationChannel> publicationChannels,
    SampleExpandedPublicationDate publicationDate,
    String instanceType,
    String abstractText,
    String language,
    String status,
    List<SampleExpandedContributor> contributors,
    List<SampleExpandedOrganization> topLevelOrganizations) {

  private static final String TEMPLATE_JSON_PATH = "template_publication.json";
  private static final String PUBLICATION_CHANNELS_MUST_NOT_BE_EMPTY =
      "Publication channels must not be empty";

  public static Builder builder() {
    return new Builder();
  }

  public static Builder from(SampleExpandedPublication other) {
    return new Builder(other);
  }

  public String toJsonString() {
    return toJsonNode().toString();
  }

  public JsonNode toJsonNode() {
    var root = objectMapper.createObjectNode();
    root.set(BODY_FIELD, createExpandedResource());
    return root;
  }

  private static ObjectNode createAndPopulatePublicationInstance(String type) {
    var publicationInstance = objectMapper.createObjectNode();
    publicationInstance.put(TYPE_FIELD, type);

    switch (type) {
      case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW, ACADEMIC_CHAPTER -> {
        var pages = objectMapper.createObjectNode();
        pages.put("begin", "pageBegin");
        pages.put("end", "pageEnd");
        publicationInstance.set(PAGES_FIELD, pages);
      }
      case ACADEMIC_MONOGRAPH -> {
        var pages = objectMapper.createObjectNode();
        pages.put(PAGES_FIELD, "numberOfPages");
        publicationInstance.set(PAGES_FIELD, pages);
      }
      default -> {
        // do nothing
      }
    }
    return publicationInstance;
  }

  private JsonNode createExpandedResource() {
    try (var content = IoUtils.inputStreamFromResources(TEMPLATE_JSON_PATH)) {
      var root = (ObjectNode) objectMapper.readTree(content);
      root.put(TYPE_FIELD, "Publication");
      root.put(ID_FIELD, id.toString());
      root.put(IDENTIFIER_FIELD, identifier.toString());
      root.put(STATUS_FIELD, status);

      root.set(ENTITY_DESCRIPTION_FIELD, createEntityDescriptionNode());
      root.set(TOP_LEVEL_ORGANIZATIONS_FIELD, createTopLevelOrganizationsNode());

      return root;
    } catch (IOException e) {
      throw new UncheckedIOException("Template could not be read", e);
    }
  }

  private JsonNode createTopLevelOrganizationsNode() {
    var topLevelOrganizationsNode = objectMapper.createArrayNode();
    topLevelOrganizations.stream()
        .map(SampleExpandedOrganization::asObjectNode)
        .forEach(topLevelOrganizationsNode::add);
    return topLevelOrganizationsNode;
  }

  private ObjectNode createReferenceNode() {
    var referenceNode = objectMapper.createObjectNode();
    referenceNode.put(TYPE_FIELD, "Reference");
    referenceNode.set(
        PUBLICATION_INSTANCE_FIELD, createAndPopulatePublicationInstance(instanceType));
    referenceNode.set(PUBLICATION_CONTEXT_FIELD, createPublicationContextNode());
    return referenceNode;
  }

  private ObjectNode createPublicationContextNode() {
    if (ACADEMIC_CHAPTER.equalsIgnoreCase(instanceType)) {
      return createNestedAnthologyContext();
    } else {
      return createFlatPublicationContext();
    }
  }

  private ObjectNode createFlatPublicationContext() {
    if (publicationChannels.isEmpty()) {
      throw new IllegalArgumentException(PUBLICATION_CHANNELS_MUST_NOT_BE_EMPTY);
    }
    if (publicationChannels.size() == ONE) {
      return publicationChannels.getFirst().asObjectNode();
    } else {
      var publicationContextNode = objectMapper.createObjectNode();
      publicationContextNode.put(TYPE_FIELD, publicationContextType);
      for (SampleExpandedPublicationChannel publicationChannel : publicationChannels) {
        publicationContextNode.set(publicationChannel.type(), publicationChannel.asObjectNode());
      }
      return publicationContextNode;
    }
  }

  private ObjectNode createNestedAnthologyContext() {
    var outerContextNode = objectMapper.createObjectNode();
    outerContextNode.put(TYPE_FIELD, "Anthology");

    var entityDescriptionNode = objectMapper.createObjectNode();
    entityDescriptionNode.put(TYPE_FIELD, "EntityDescription");
    outerContextNode.set(ENTITY_DESCRIPTION_FIELD, entityDescriptionNode);

    var referenceNode = objectMapper.createObjectNode();
    referenceNode.put(TYPE_FIELD, "Reference");
    entityDescriptionNode.set(REFERENCE_FIELD, referenceNode);

    var innerContextNode = objectMapper.createObjectNode();
    innerContextNode.put(TYPE_FIELD, "Report");
    for (SampleExpandedPublicationChannel publicationChannel : publicationChannels) {
      innerContextNode.set(publicationChannel.type(), publicationChannel.asObjectNode());
    }
    referenceNode.set(PUBLICATION_CONTEXT_FIELD, innerContextNode);

    return outerContextNode;
  }

  private ObjectNode createEntityDescriptionNode() {
    var entityDescriptionNode = objectMapper.createObjectNode();
    entityDescriptionNode.put(TYPE_FIELD, "EntityDescription");
    entityDescriptionNode.put(MAIN_TITLE_FIELD, mainTitle);
    if (nonNull(language)) {
      entityDescriptionNode.put(LANGUAGE_FIELD, language);
    }
    if (nonNull(abstractText)) {
      entityDescriptionNode.put(ABSTRACT_FIELD, abstractText);
    }
    entityDescriptionNode.set(CONTRIBUTORS_FIELD, createContributorsNode());
    entityDescriptionNode.set(PUBLICATION_DATE_FIELD, publicationDate.asObjectNode());
    entityDescriptionNode.set(REFERENCE_FIELD, createReferenceNode());
    return entityDescriptionNode;
  }

  private ArrayNode createContributorsNode() {
    var contributorsNode = objectMapper.createArrayNode();
    contributors.stream()
        .map(SampleExpandedContributor::asObjectNode)
        .forEach(contributorsNode::add);
    return contributorsNode;
  }

  @JacocoGenerated
  public static final class Builder {

    private URI id;
    private UUID identifier = UUID.randomUUID();
    private String mainTitle = randomString();
    private List<SampleExpandedContributor> contributors;
    private String status = "PUBLISHED";
    private String language;
    private String abstractText;
    private String publicationContextType = "Book";
    private String instanceType = ACADEMIC_ARTICLE;
    private List<SampleExpandedPublicationChannel> publicationChannels;
    private SampleExpandedPublicationDate publicationDate;
    private String issn;
    private List<SampleExpandedOrganization> topLevelOrganizations;

    private Builder() {}

    private Builder(SampleExpandedPublication other) {
      this.id = other.id;
      this.identifier = other.identifier;
      this.mainTitle = other.mainTitle;
      this.contributors = other.contributors;
      this.status = other.status;
      this.language = other.language;
      this.abstractText = other.abstractText;
      this.publicationContextType = other.publicationContextType;
      this.instanceType = other.instanceType;
      this.publicationChannels = other.publicationChannels;
      this.publicationDate = other.publicationDate;
      this.issn = other.issn;
      this.topLevelOrganizations = other.topLevelOrganizations;
    }

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withContributors(List<SampleExpandedContributor> contributors) {
      this.contributors = contributors;
      return this;
    }

    public Builder withPublicationChannels(
        List<SampleExpandedPublicationChannel> publicationChannels) {
      this.publicationChannels = publicationChannels;
      return this;
    }

    public Builder withInstanceType(String instanceType) {
      this.instanceType = instanceType;
      return this;
    }

    public Builder withPublicationDate(SampleExpandedPublicationDate publicationDate) {
      this.publicationDate = publicationDate;
      return this;
    }

    public Builder withTopLevelOrganizations(List<SampleExpandedOrganization> organizations) {
      this.topLevelOrganizations = organizations;
      return this;
    }

    public SampleExpandedPublication build() {
      if (isNull(id)) {
        id = generatePublicationId(identifier);
      }
      return new SampleExpandedPublication(
          id,
          identifier,
          mainTitle,
          issn,
          publicationContextType,
          publicationChannels,
          publicationDate,
          instanceType,
          abstractText,
          language,
          status,
          contributors,
          topLevelOrganizations);
    }
  }
}
