package no.sikt.nva.nvi.common.service;

import static nva.commons.core.ioutils.IoUtils.stringToStream;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import nva.commons.core.JacocoGenerated;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record NvaGraph(Model model) implements GraphValidable {

  private static final Logger LOGGER = LoggerFactory.getLogger(NvaGraph.class);

  public static NvaGraph fromJsonLd(JsonNode content) {
    var model = ModelFactory.createDefaultModel();
    loadJsonLdIntoModel(model, stringToStream(content.toString()));
    return new NvaGraph(model);
  }

  public static NvaGraph fromNtriples(JsonNode content) {
    var model = ModelFactory.createDefaultModel();
    loadNtriplesIntoModel(model, stringToStream(content.asText()));
    return new NvaGraph(model);
  }

  @Override
  public GraphValidation validate(GraphValidator validator) {
    return validator.validate(model);
  }

  public NviGraph toNviGraph() {
    return NviGraph.fromNvaGraph(this);
  }

  private static void loadJsonLdIntoModel(Model model, InputStream inputStream) {
    try {
      RDFDataMgr.read(model, inputStream, Lang.JSONLD);
    } catch (RiotException e) {
      logInvalidJsonLdInput(e);
    }
  }

  private static void loadNtriplesIntoModel(Model model, InputStream inputStream) {
    try {
      RDFDataMgr.read(model, inputStream, Lang.NTRIPLES);
    } catch (RiotException e) {
      logInvalidJsonLdInput(e);
    }
  }

  @JacocoGenerated
  private static void logInvalidJsonLdInput(Exception exception) {
    LOGGER.warn("Invalid JSON LD input encountered: ", exception);
  }
}
