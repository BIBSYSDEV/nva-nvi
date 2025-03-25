package no.sikt.nva.nvi.common.utils;

import static java.util.Objects.isNull;
import static nva.commons.core.ioutils.IoUtils.stringToStream;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.io.StringWriter;
import nva.commons.core.JacocoGenerated;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GraphUtils {
  public static final String PART_OF_PROPERTY = "https://nva.sikt.no/ontology/publication#partOf";
  public static final String HAS_PART_PROPERTY = "https://nva.sikt.no/ontology/publication#hasPart";
  private static final Logger LOGGER = LoggerFactory.getLogger(GraphUtils.class);

  private GraphUtils() {}

  public static Model createModel(JsonNode body) {
    var model = ModelFactory.createDefaultModel();
    loadDataIntoModel(model, stringToStream(body.toString()));
    return model;
  }

  @JacocoGenerated
  public static String toTurtle(Model model) {
    StringWriter stringWriter = new StringWriter();
    RDFDataMgr.write(stringWriter, model, Lang.TURTLE);
    return stringWriter.toString();
  }

  @JacocoGenerated
  public static String toNTriples(Model model) {
    StringWriter stringWriter = new StringWriter();
    RDFDataMgr.write(stringWriter, model, Lang.NTRIPLES);
    return stringWriter.toString();
  }

  @JacocoGenerated
  public static String toJsonLd(Model model) {
    StringWriter stringWriter = new StringWriter();
    RDFDataMgr.write(stringWriter, model, Lang.JSONLD);
    return stringWriter.toString();
  }

  private static void loadDataIntoModel(Model model, InputStream inputStream) {
    if (isNull(inputStream)) {
      return;
    }
    try {
      RDFDataMgr.read(model, inputStream, Lang.JSONLD);
    } catch (RiotException e) {
      logInvalidJsonLdInput(e);
    }
  }

  @JacocoGenerated
  private static void logInvalidJsonLdInput(Exception exception) {
    LOGGER.warn("Invalid JSON LD input encountered: ", exception);
  }
}
