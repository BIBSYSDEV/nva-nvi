package no.sikt.nva.nvi.rdf;

import static nva.commons.core.ioutils.IoUtils.inputStreamFromResources;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.JsonDocument;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 * A JSON-LD frame loaded from a classpath resource. It serializes a Jena {@link Model} and reshapes
 * it into framed JSON-LD, so different consumers can extract different shapes from the same graph.
 */
public final class JsonLdFrame {

  private final JsonDocument frame;

  private JsonLdFrame(JsonDocument frame) {
    this.frame = frame;
  }

  public static JsonLdFrame fromResource(String fileName) {
    try {
      return new JsonLdFrame(JsonDocument.of(inputStreamFromResources(fileName)));
    } catch (JsonLdError e) {
      throw new RdfProcessingException(e.getMessage());
    }
  }

  public String apply(Model model) {
    try {
      var document = JsonDocument.of(toJsonReader(model));
      return JsonLd.frame(document, frame).get().toString();
    } catch (JsonLdError e) {
      throw new RdfProcessingException(e.getMessage());
    }
  }

  private static StringReader toJsonReader(Model model) {
    var outputStream = new ByteArrayOutputStream();
    RDFDataMgr.write(outputStream, model, Lang.JSONLD);
    return new StringReader(outputStream.toString(StandardCharsets.UTF_8));
  }
}
