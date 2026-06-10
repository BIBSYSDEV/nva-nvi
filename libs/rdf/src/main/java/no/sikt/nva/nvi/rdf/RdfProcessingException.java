package no.sikt.nva.nvi.rdf;

/** Thrown when reading, framing or otherwise processing an RDF graph fails. */
public class RdfProcessingException extends RuntimeException {

  public RdfProcessingException(String message) {
    super(message);
  }
}
