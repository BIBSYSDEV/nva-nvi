package no.sikt.nva.nvi.rdf;

import static nva.commons.core.ioutils.IoUtils.stringFromResources;

import java.nio.file.Path;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;

/**
 * A named SPARQL CONSTRUCT query that projects triples out of a source graph. Instances are
 * reusable and composable: each one reads a source {@link Model} and returns the constructed
 * triples without mutating the source, so several projections can be run over the same graph and
 * their results merged.
 */
public record SparqlConstruct(String name, String query) {

  public static SparqlConstruct fromResource(String fileName) {
    return new SparqlConstruct(fileName, stringFromResources(Path.of(fileName)));
  }

  public Model projectFrom(Model source) {
    try (var queryExecution = QueryExecutionFactory.create(query, source)) {
      return queryExecution.execConstruct();
    }
  }
}
