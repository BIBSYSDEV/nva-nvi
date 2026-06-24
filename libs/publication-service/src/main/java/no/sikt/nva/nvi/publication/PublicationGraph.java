package no.sikt.nva.nvi.publication;

import static no.sikt.nva.nvi.rdf.JsonLdModels.createModel;

import com.fasterxml.jackson.databind.JsonNode;
import no.sikt.nva.nvi.rdf.GraphValidable;
import no.sikt.nva.nvi.rdf.GraphValidation;
import no.sikt.nva.nvi.rdf.GraphValidator;
import org.apache.jena.rdf.model.Model;

/**
 * An expanded NVA publication parsed into a source graph. It is validated against the NVA shape in
 * isolation before being merged into the working graph.
 */
public record PublicationGraph(Model model) implements GraphValidable {

  public static PublicationGraph fromJsonLd(JsonNode content) {
    return new PublicationGraph(createModel(content));
  }

  @Override
  public GraphValidation validate(GraphValidator validator) {
    return validator.validate(model);
  }
}
