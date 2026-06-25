package no.sikt.nva.nvi.index.utils;

import java.net.URI;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.model.PersistedResource;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.Environment;

/**
 * Wires the legacy JsonNode-based generator: reads the expanded resource JSON from S3 and hands it
 * to {@link NviCandidateIndexDocumentGenerator}, which still fetches organization hierarchies via
 * {@link UriRetriever} on demand.
 */
public final class JsonNodeIndexDocumentGeneratorFactory implements IndexDocumentGeneratorFactory {

  private final StorageReader<URI> storageReader;
  private final UriRetriever uriRetriever;
  private final Environment environment;

  public JsonNodeIndexDocumentGeneratorFactory(
      StorageReader<URI> storageReader, UriRetriever uriRetriever, Environment environment) {
    this.storageReader = storageReader;
    this.uriRetriever = uriRetriever;
    this.environment = environment;
  }

  @Override
  public IndexDocumentGenerator forCandidate(Candidate candidate) {
    var persistedResource =
        PersistedResource.fromUri(
            candidate.publicationDetails().publicationBucketUri(), storageReader);
    return new NviCandidateIndexDocumentGenerator(
        uriRetriever, persistedResource.getExpandedResource(), candidate, environment);
  }
}
