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
import static no.sikt.nva.nvi.test.TestConstants.ENTITY_DESCRIPTION_TYPE;
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
import static no.sikt.nva.nvi.test.TestConstants.REFERENCE_TYPE;
import static no.sikt.nva.nvi.test.TestConstants.STATUS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TOP_LEVEL_ORGANIZATIONS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.createNodeWithType;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotBlank;
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
    SampleExpandedPageCount pageCount,
    List<SampleExpandedPublicationChannel> publicationChannels,
    SampleExpandedPublicationDate publicationDate,
    String instanceType,
    String abstractText,
    String language,
    String status,
    String modifiedDate,
    List<SampleExpandedContributor> contributors,
    List<SampleExpandedOrganization> topLevelOrganizations) {

  private static final String TEMPLATE_JSON_PATH = "template_publication.json";
  private static final String PUBLICATION_CHANNELS_MUST_NOT_BE_EMPTY =
      "Publication channels must not be empty";

  public static Builder builder() {
    return new Builder();
  }

  public String toJsonString() {
    return toJsonNode().toString();
  }

  public JsonNode toJsonNode() {
    var root = objectMapper.createObjectNode();
    root.set(BODY_FIELD, createExpandedResource());
    return root;
  }

  private ObjectNode createAndPopulatePublicationInstance(String type) {
    var publicationInstance = objectMapper.createObjectNode();
    publicationInstance.put(TYPE_FIELD, type);

    switch (type) {
      case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW, ACADEMIC_CHAPTER -> {
        var pages = pageCount.asPageRange();
        publicationInstance.set(PAGES_FIELD, pages);
      }
      case ACADEMIC_MONOGRAPH -> {
        var pages = pageCount.asMonographPages();
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
      putIfNotBlank(root, "modifiedDate", modifiedDate);

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
    var node = createNodeWithType(REFERENCE_TYPE);
    node.set(PUBLICATION_INSTANCE_FIELD, createAndPopulatePublicationInstance(instanceType));
    node.set(PUBLICATION_CONTEXT_FIELD, createPublicationContextNode());
    return node;
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
      var node = createNodeWithType(publicationContextType);
      for (SampleExpandedPublicationChannel publicationChannel : publicationChannels) {
        node.set(publicationChannel.type(), publicationChannel.asObjectNode());
      }
      return node;
    }
  }

  private ObjectNode createNestedAnthologyContext() {
    var node = createNodeWithType("Anthology");

    var entityDescriptionNode = createNodeWithType(ENTITY_DESCRIPTION_TYPE);
    node.set(ENTITY_DESCRIPTION_FIELD, entityDescriptionNode);

    var referenceNode = createNodeWithType(REFERENCE_TYPE);
    entityDescriptionNode.set(REFERENCE_FIELD, referenceNode);

    var innerContextNode = createNodeWithType("Report");
    for (SampleExpandedPublicationChannel publicationChannel : publicationChannels) {
      innerContextNode.set(publicationChannel.type(), publicationChannel.asObjectNode());
    }
    referenceNode.set(PUBLICATION_CONTEXT_FIELD, innerContextNode);

    return node;
  }

  private ObjectNode createEntityDescriptionNode() {
    var node = createNodeWithType(ENTITY_DESCRIPTION_FIELD);
    putIfNotBlank(node, LANGUAGE_FIELD, language);
    putIfNotBlank(node, ABSTRACT_FIELD, abstractText);
    node.put(MAIN_TITLE_FIELD, mainTitle);
    node.set(CONTRIBUTORS_FIELD, createContributorsNode());
    if (nonNull(publicationDate)) {
      node.set(PUBLICATION_DATE_FIELD, publicationDate.asObjectNode());
    }
    node.set(REFERENCE_FIELD, createReferenceNode());
    return node;
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
    private String title = randomString();
    private String status = "PUBLISHED";
    private String language;
    private String abstractText;
    private String issn;
    private SampleExpandedPageCount pageCount = new SampleExpandedPageCount(null, null, null);
    private SampleExpandedPublicationDate publicationDate;
    private String instanceType = ACADEMIC_ARTICLE;
    private String publicationContextType = "Book";
    private String modifiedDate;
    private List<SampleExpandedPublicationChannel> publicationChannels;
    private List<SampleExpandedContributor> contributors;
    private List<SampleExpandedOrganization> topLevelOrganizations;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withIdentifier(UUID identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder withTitle(String title) {
      this.title = title;
      return this;
    }

    public Builder withStatus(String status) {
      this.status = status;
      return this;
    }

    public Builder withLanguage(String language) {
      this.language = language;
      return this;
    }

    public Builder withAbstract(String abstractText) {
      this.abstractText = abstractText;
      return this;
    }

    public Builder withIssn(String issn) {
      this.issn = issn;
      return this;
    }

    public Builder withPageCount(String begin, String end, String numberOfPages) {
      this.pageCount = new SampleExpandedPageCount(begin, end, numberOfPages);
      return this;
    }

    public Builder withPublicationDate(SampleExpandedPublicationDate publicationDate) {
      this.publicationDate = publicationDate;
      return this;
    }

    public Builder withInstanceType(String instanceType) {
      this.instanceType = instanceType;
      return this;
    }

    public Builder withPublicationContextType(String publicationContextType) {
      this.publicationContextType = publicationContextType;
      return this;
    }

    public Builder withModifiedDate(String modifiedDate) {
      this.modifiedDate = modifiedDate;
      return this;
    }

    public Builder withPublicationChannels(
        List<SampleExpandedPublicationChannel> publicationChannels) {
      this.publicationChannels = publicationChannels;
      return this;
    }

    public Builder withContributors(List<SampleExpandedContributor> contributors) {
      this.contributors = contributors;
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
          title,
          issn,
          publicationContextType,
          pageCount,
          publicationChannels,
          publicationDate,
          instanceType,
          abstractText,
          language,
          status,
          modifiedDate,
          contributors,
          topLevelOrganizations);
    }
  }
}
