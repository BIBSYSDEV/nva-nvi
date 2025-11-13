package no.sikt.nva.nvi.test;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.test.TestConstants.ENTITY_DESCRIPTION_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ENTITY_DESCRIPTION_TYPE;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.MAIN_TITLE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ONE;
import static no.sikt.nva.nvi.test.TestConstants.PUBLICATION_CONTEXT_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.REFERENCE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.REFERENCE_TYPE;
import static no.sikt.nva.nvi.test.TestUtils.createNodeWithType;
import static no.sikt.nva.nvi.test.TestUtils.putAsArray;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotBlank;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotNull;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    Map<String, SampleExpandedPublicationChannel> publicationChannels) {

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
      String contextType,
      Map<String, SampleExpandedPublicationChannel> publicationChannels,
      Collection<String> isbnList,
      String revisionStatus) {
    if (publicationChannels.isEmpty()) {
      throw new IllegalArgumentException(PUBLICATION_CHANNELS_MUST_NOT_BE_EMPTY);
    }
    return builder()
        .withType(resolveContextType(contextType, publicationChannels))
        .withPublicationChannels(publicationChannels)
        .withIsbnList(isbnList)
        .withRevision(revisionStatus)
        .build();
  }

  public static SampleExpandedPublicationContext createAnthologyContext(
      Map<String, SampleExpandedPublicationChannel> publicationChannels,
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
    return ANTHOLOGY_TYPE.equals(type) ? createNestedAnthologyNode() : createSingleContextNode();
  }

  private static String resolveContextType(
      String publicationContextType,
      Map<String, SampleExpandedPublicationChannel> publicationChannels) {
    return publicationChannels.size() == ONE
        ? publicationChannels.keySet().iterator().next()
        : publicationContextType;
  }

  private ObjectNode createContextWithSingleChannel() {
    return publicationChannels.values().iterator().next().asObjectNode();
  }

  private ObjectNode createContextWithMultipleChannels() {
    var contextNode = createNodeWithType(type);
    for (var entry : publicationChannels.entrySet()) {
      var fieldName = entry.getKey().toLowerCase(Locale.ROOT);
      var channel = entry.getValue().asObjectNode();
      contextNode.set(fieldName, channel);
    }
    return contextNode;
  }

  private ObjectNode createSingleContextNode() {
    var contextNode =
        publicationChannels.size() == ONE
            ? createContextWithSingleChannel()
            : createContextWithMultipleChannels();

    putAsArray(contextNode, ISBN_FIELD, isbnList);
    putIfNotBlank(contextNode, REVISION_FIELD, revision);

    return contextNode;
  }

  private ObjectNode createNestedAnthologyNode() {
    var anthologyContextNode = createNodeWithType(ANTHOLOGY_TYPE);
    putIfNotNull(anthologyContextNode, ID_FIELD, id);

    var entityDescriptionNode = createNodeWithType(ENTITY_DESCRIPTION_TYPE);
    putIfNotBlank(entityDescriptionNode, MAIN_TITLE_FIELD, mainTitle);
    anthologyContextNode.set(ENTITY_DESCRIPTION_FIELD, entityDescriptionNode);

    var referenceNode = createNodeWithType(REFERENCE_TYPE);
    referenceNode.set(PUBLICATION_CONTEXT_FIELD, nestedPublicationContext.asObjectNode());
    entityDescriptionNode.set(REFERENCE_FIELD, referenceNode);

    return anthologyContextNode;
  }

  @JacocoGenerated
  public static final class Builder {

    private String type;
    private URI id;
    private String revision;
    private String mainTitle;
    private SampleExpandedPublicationContext nestedPublicationContext;
    private Collection<String> isbnList;
    private Map<String, SampleExpandedPublicationChannel> publicationChannels = emptyMap();

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
      this.isbnList = List.copyOf(isbnList);
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
        Map<String, SampleExpandedPublicationChannel> publicationChannels) {
      this.publicationChannels = Map.copyOf(publicationChannels);
      return this;
    }

    public SampleExpandedPublicationContext build() {
      return new SampleExpandedPublicationContext(
          type, id, isbnList, revision, mainTitle, nestedPublicationContext, publicationChannels);
    }
  }
}
