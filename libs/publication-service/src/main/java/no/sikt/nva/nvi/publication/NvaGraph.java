package no.sikt.nva.nvi.publication;

import static no.sikt.nva.nvi.rdf.JsonLdModels.createModel;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.jena.rdf.model.Model;

public record NvaGraph(Model model) implements GraphValidable {

  public static NvaGraph fromJsonLd(JsonNode content) {
    return new NvaGraph(createModel(content));
  }

  @Override
  public GraphValidation validate(GraphValidator validator) {
    return validator.validate(model);
  }

  public NviGraph toNviGraph() {
    return NviGraph.fromNvaGraph(this);
  }
}
