package no.sikt.nva.nvi.index.mapper;

import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.publication.PublicationLoaderService;
import nva.commons.core.Environment;

/**
 * Generates index documents from a persisted {@link Candidate}, enriched with the expanded
 * publication loaded from S3. There are no live lookups. An expanded publication that cannot be
 * parsed is logged and tolerated, producing a document from candidate data alone.
 */
public final class IndexDocumentGenerator {

  private final PublicationLoaderService publicationLoaderService;
  private final Environment environment;

  public IndexDocumentGenerator(
      PublicationLoaderService publicationLoaderService, Environment environment) {
    this.publicationLoaderService = publicationLoaderService;
    this.environment = environment;
  }

  public NviCandidateIndexDocument generate(Candidate candidate) {
    var publicationBucketUri = candidate.publicationDetails().publicationBucketUri();
    var publicationDto =
        publicationLoaderService.tryExtractAndTransform(publicationBucketUri).orElse(null);
    return new CandidateToIndexDocumentMapper(candidate, publicationDto, environment).generate();
  }
}
