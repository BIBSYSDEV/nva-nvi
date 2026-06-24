package no.sikt.nva.nvi.rdf;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * A mutable working graph that assembles triples from one or more source graphs, can validate
 * itself (SHACL), project itself through SPARQL CONSTRUCT queries, and frame the result into
 * JSON-LD.
 *
 * <p>The graph owns a private Jena {@link Model}: {@link #of(Model)} and {@link #add(Model)} copy
 * triples in rather than adopting the caller's model, so a cached or otherwise shared source model
 * is never mutated and can be reused safely across invocations.
 */
public final class Graph implements GraphValidable {

  private final Model model;

  private Graph(Model model) {
    this.model = model;
  }

  public static Graph of(Model source) {
    return new Graph(ModelFactory.createDefaultModel().add(source));
  }

  public Graph add(Model source) {
    model.add(source);
    return this;
  }

  @Override
  public GraphValidation validate(GraphValidator validator) {
    return validator.validate(model);
  }

  public Graph project(GraphProjectionPipeline pipeline) {
    return new Graph(pipeline.project(model));
  }

  public String frame(JsonLdFrame frame) {
    return frame.apply(model);
  }
}
