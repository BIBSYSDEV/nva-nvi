package no.sikt.nva.nvi.common.service;

import static nva.commons.core.ioutils.IoUtils.inputStreamFromResources;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.JsonDocument;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import no.sikt.nva.nvi.common.exceptions.ParsingException;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

public class NviGraph implements GraphValidable {

  private static final JsonDocument OUTPUT_FRAMING_CONTEXT = getOutputFramingContext();
  private static final String OUTPUT_FRAMING_CONTEXT_FILE = "publication_frame.json";
  private static final String SPARQL_QUERY =
      stringFromResources(Path.of("publication_query.sparql"));
  private final Model model;

  public NviGraph(Model model) {
    this.model = model;
  }

  public String toJsonLd() {
    try {
      var document = JsonDocument.of(toJsonReader(model));
      return JsonLd.frame(document, OUTPUT_FRAMING_CONTEXT).get().toString();
    } catch (JsonLdError e) {
      throw new ParsingException(e.getMessage());
    }
  }

  public static NviGraph fromNvaGraph(NvaGraph nvaGraph) {
    try (var queryExecution = QueryExecutionFactory.create(SPARQL_QUERY, nvaGraph.model())) {
      return new NviGraph(queryExecution.execConstruct());
    }
  }

  @Override
  public GraphValidation validate(GraphValidator validator) {
    return validator.validate(model);
  }

  private static StringReader toJsonReader(Model resultModel) {
    var outputStream = new ByteArrayOutputStream();
    RDFDataMgr.write(outputStream, resultModel, Lang.JSONLD);
    return new StringReader(outputStream.toString(StandardCharsets.UTF_8));
  }

  private static JsonDocument getOutputFramingContext() {
    try {
      return JsonDocument.of(inputStreamFromResources(OUTPUT_FRAMING_CONTEXT_FILE));
    } catch (JsonLdError e) {
      throw new ParsingException(e.getMessage());
    }
  }
}
