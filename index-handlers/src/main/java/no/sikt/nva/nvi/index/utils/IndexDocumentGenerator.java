package no.sikt.nva.nvi.index.utils;

import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;

/**
 * Builds an {@link NviCandidateIndexDocument} from a candidate and whatever supplementary inputs an
 * implementation needs to supply. Lets callers swap implementations without knowing which strategy
 * (expanded-resource JSON vs SPARQL-parsed DTO) is used to assemble the document.
 */
@FunctionalInterface
public interface IndexDocumentGenerator {

  NviCandidateIndexDocument generate();
}
