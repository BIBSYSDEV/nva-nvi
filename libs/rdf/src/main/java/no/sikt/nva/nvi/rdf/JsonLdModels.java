package no.sikt.nva.nvi.rdf;

import static nva.commons.core.ioutils.IoUtils.stringToStream;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JsonLdModels {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonLdModels.class);

  private JsonLdModels() {}

  public static Model createModel(JsonNode jsonLd) {
    var model = ModelFactory.createDefaultModel();
    try {
      RDFDataMgr.read(model, stringToStream(jsonLd.toString()), Lang.JSONLD);
    } catch (RiotException exception) {
      LOGGER.warn("Invalid JSON-LD input encountered: ", exception);
    }
    return model;
  }
}
