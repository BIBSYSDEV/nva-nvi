package no.sikt.nva.nvi.rdf;

import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * Applies an ordered list of {@link SparqlConstruct} projections to a source graph, accumulating
 * every projection's output into a single result graph. Each projection reads the same untouched
 * source, so the steps compose: adding a projection adds data to the result without disturbing the
 * others. This is the reusable spine shared by graph-shaped pipelines (for example building an NVI
 * candidate from an NVA publication, or a report from aggregated search data).
 */
public class GraphProjectionPipeline {

  private final List<SparqlConstruct> projections;

  public GraphProjectionPipeline(List<SparqlConstruct> projections) {
    this.projections = List.copyOf(projections);
  }

  public Model project(Model source) {
    var result = ModelFactory.createDefaultModel();
    for (var projection : projections) {
      result.add(projection.projectFrom(source));
    }
    return result;
  }
}
