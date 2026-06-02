package no.sikt.nva.nvi.index.utils;

import no.sikt.nva.nvi.common.service.model.Candidate;

/**
 * Produces an {@link IndexDocumentGenerator} for a given candidate. Each implementation
 * encapsulates whatever supplementary data fetching (S3 reads, DTO loading, etc.) the chosen
 * generator strategy requires, so the {@code IndexDocumentHandler} doesn't have to know.
 */
@FunctionalInterface
public interface IndexDocumentGeneratorFactory {

  IndexDocumentGenerator forCandidate(Candidate candidate);
}
