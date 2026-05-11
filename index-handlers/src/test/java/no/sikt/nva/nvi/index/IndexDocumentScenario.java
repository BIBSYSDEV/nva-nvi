package no.sikt.nva.nvi.index;

import com.fasterxml.jackson.databind.JsonNode;
import no.sikt.nva.nvi.common.service.model.Candidate;

/**
 * Source-of-truth fixture for index-document tests. Holds a {@link Candidate} and the views of it
 * that the document generators consume. Each generator strategy reads a different view (the old
 * mapper reads the expanded-resource JSON; the new mapper will read a {@code PublicationDto}), but
 * both views are derived from the same scenario so equivalence tests can run against either.
 *
 * <p>Test classes hold a {@code handlerFor(scenario)} method that wires the scenario into an {@code
 * IndexDocumentHandler} — that method is the swap point between mapper implementations.
 */
public final class IndexDocumentScenario {

  private final Candidate candidate;
  private final JsonNode expandedResource;

  private IndexDocumentScenario(Candidate candidate, JsonNode expandedResource) {
    this.candidate = candidate;
    this.expandedResource = expandedResource;
  }

  public static IndexDocumentScenario forCandidate(Candidate candidate) {
    var expandedResource =
        ExpandedResourceGenerator.builder()
            .withCandidate(candidate)
            .build()
            .createExpandedResource();
    return new IndexDocumentScenario(candidate, expandedResource);
  }

  public Candidate candidate() {
    return candidate;
  }

  public JsonNode expandedResource() {
    return expandedResource;
  }
}
