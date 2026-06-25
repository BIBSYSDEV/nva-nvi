package no.sikt.nva.nvi.index.utils;

import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.publication.PublicationLoaderService;
import nva.commons.core.Environment;

/**
 * Wires the new SPARQL/DTO-based generator: loads a {@code PublicationDto} via {@link
 * PublicationLoaderService} and hands it to {@link CandidateToIndexDocumentMapper}, which then
 * needs no further network or storage access.
 */
public final class PublicationDtoIndexDocumentGeneratorFactory
    implements IndexDocumentGeneratorFactory {

  private final PublicationLoaderService publicationLoaderService;
  private final Environment environment;

  public PublicationDtoIndexDocumentGeneratorFactory(
      PublicationLoaderService publicationLoaderService, Environment environment) {
    this.publicationLoaderService = publicationLoaderService;
    this.environment = environment;
  }

  @Override
  public IndexDocumentGenerator forCandidate(Candidate candidate) {
    var publicationDto =
        publicationLoaderService.extractAndTransform(
            candidate.publicationDetails().publicationBucketUri());
    return new CandidateToIndexDocumentMapper(candidate, publicationDto, environment);
  }
}
