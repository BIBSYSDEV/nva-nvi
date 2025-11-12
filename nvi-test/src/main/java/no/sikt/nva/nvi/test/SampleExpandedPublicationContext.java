package no.sikt.nva.nvi.test;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.ENTITY_DESCRIPTION_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ENTITY_DESCRIPTION_TYPE;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.MAIN_TITLE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ONE;
import static no.sikt.nva.nvi.test.TestConstants.PUBLICATION_CONTEXT_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.REFERENCE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.REFERENCE_TYPE;
import static no.sikt.nva.nvi.test.TestUtils.createNodeWithType;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotBlank;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Collection;
import nva.commons.core.JacocoGenerated;

/**
 * Represents an expanded publication context, which can be either a simple context (Book, Journal,
 * etc.) or a nested context (Anthology containing another publication context).
 */
@JacocoGenerated
public record SampleExpandedPublicationContext(
    String type,
    URI id,
    Collection<String> isbnList,
    String revision,
    String mainTitle,
    SampleExpandedPublicationContext nestedPublicationContext,
    Collection<SampleExpandedPublicationChannel> publicationChannels) {

  private static final String PUBLICATION_CHANNELS_MUST_NOT_BE_EMPTY =
      "Publication channels must not be empty";
  private static final String ANTHOLOGY_TYPE = "Anthology";
  private static final String BOOK_TYPE = "Book";
  private static final String ISBN_FIELD = "isbnList";
  private static final String REVISION_FIELD = "revision";

  public static Builder builder() {
    return new Builder();
  }

  public static SampleExpandedPublicationContext createFlatPublicationContext(
      String publicationContextType,
      Collection<SampleExpandedPublicationChannel> publicationChannels,
      Collection<String> isbnList,
      String revisionStatus) {
    if (publicationChannels.isEmpty()) {
      throw new IllegalArgumentException(PUBLICATION_CHANNELS_MUST_NOT_BE_EMPTY);
    }
    var builder =
        builder()
            .withPublicationChannels(publicationChannels)
            .withIsbnList(isbnList)
            .withRevision(revisionStatus);
    if (publicationChannels.size() == ONE) {
      var channel = publicationChannels.iterator().next();
      return builder.withType(channel.type()).build();
    }
    return builder.withType(publicationContextType).build();
  }

  public static SampleExpandedPublicationContext createAnthologyContext(
      Collection<SampleExpandedPublicationChannel> publicationChannels,
      Collection<String> isbnList,
      URI anthologyId,
      String mainTitle,
      String revisionStatus) {
    var nestedContext =
        createFlatPublicationContext(BOOK_TYPE, publicationChannels, isbnList, revisionStatus);
    return builder()
        .withType(ANTHOLOGY_TYPE)
        .withId(anthologyId)
        .withMainTitle(mainTitle)
        .withNestedPublicationContext(nestedContext)
        .build();
  }

  public ObjectNode asObjectNode() {
    if (ANTHOLOGY_TYPE.equals(type)) {
      return createNestedAnthologyNode();
    } else {
      return createSimpleContextNode();
    }
  }

  private ObjectNode createSimpleContextNode() {
    if (publicationChannels.size() == ONE) {
      return publicationChannels.iterator().next().asObjectNode();
    }

    var node = createNodeWithType(type);
    for (SampleExpandedPublicationChannel channel : publicationChannels) {
      node.set(channel.type(), channel.asObjectNode());
    }

    if (nonNull(isbnList) && !isbnList.isEmpty()) {
      node.set(ISBN_FIELD, objectMapper.valueToTree(isbnList));
    }

    putIfNotBlank(node, REVISION_FIELD, revision);

    return node;
  }

  private ObjectNode createNestedAnthologyNode() {
    var node = createNodeWithType(ANTHOLOGY_TYPE);

    if (nonNull(id)) {
      node.put(ID_FIELD, id.toString());
    }

    var entityDescriptionNode = createNodeWithType(ENTITY_DESCRIPTION_TYPE);
    putIfNotBlank(entityDescriptionNode, MAIN_TITLE_FIELD, mainTitle);
    node.set(ENTITY_DESCRIPTION_FIELD, entityDescriptionNode);

    var referenceNode = createNodeWithType(REFERENCE_TYPE);
    if (nonNull(nestedPublicationContext)) {
      referenceNode.set(PUBLICATION_CONTEXT_FIELD, nestedPublicationContext.asObjectNode());
    }
    entityDescriptionNode.set(REFERENCE_FIELD, referenceNode);

    return node;
  }

  @JacocoGenerated
  public static final class Builder {

    private String type;
    private URI id;
    private String revision;
    private String mainTitle;
    private SampleExpandedPublicationContext nestedPublicationContext;
    private Collection<String> isbnList;
    private Collection<SampleExpandedPublicationChannel> publicationChannels = emptyList();

    private Builder() {}

    public Builder withType(String type) {
      this.type = type;
      return this;
    }

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withIsbnList(Collection<String> isbnList) {
      this.isbnList = isbnList;
      return this;
    }

    public Builder withRevision(String revision) {
      this.revision = revision;
      return this;
    }

    public Builder withMainTitle(String mainTitle) {
      this.mainTitle = mainTitle;
      return this;
    }

    public Builder withNestedPublicationContext(
        SampleExpandedPublicationContext nestedPublicationContext) {
      this.nestedPublicationContext = nestedPublicationContext;
      return this;
    }

    public Builder withPublicationChannels(
        Collection<SampleExpandedPublicationChannel> publicationChannels) {
      this.publicationChannels = publicationChannels;
      return this;
    }

    public SampleExpandedPublicationContext build() {
      return new SampleExpandedPublicationContext(
          type, id, isbnList, revision, mainTitle, nestedPublicationContext, publicationChannels);
    }
  }
}
