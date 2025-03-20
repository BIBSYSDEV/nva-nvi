package no.sikt.nva.nvi.common.etl;

import static no.sikt.nva.nvi.common.utils.GraphUtils.createModel;
import static no.sikt.nva.nvi.common.utils.GraphUtils.toTurtle;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.JsonDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Path;
import no.sikt.nva.nvi.common.StorageReader;
import nva.commons.core.ioutils.IoUtils;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExpandedPublicationTransformer {
  private static final String JSON_PTR_BODY = "/body";
  private static final String CONTEXT_NODE = "@context";
  private static final String JSON_PTR_CONTEXT = "/@context";
  private static final String NVI_CONTEXT_JSONLD = "nvi_context.json";
  private static final String NVA_CONTEXT_JSONLD = "nva_context.json";
  private static final String PUBLICATION_SPARQL =
      IoUtils.stringFromResources(Path.of("sparql/publication.sparql"));
  private final Logger logger = LoggerFactory.getLogger(ExpandedPublicationTransformer.class);
  private final StorageReader<URI> storageReader;
  private static final String contextString =
      IoUtils.stringFromResources(Path.of(NVA_CONTEXT_JSONLD));

  public ExpandedPublicationTransformer(StorageReader<URI> storageReader) {
    this.storageReader = storageReader;
  }

  public ExpandedPublication extractAndTransform(URI publicationBucketUri) {
    var model = extract(publicationBucketUri);
    return transform(model);
  }

  public Model extract(URI publicationBucketUri) {
    var document = storageReader.read(publicationBucketUri);
    var body = extractBody(document);
    var turtles = toTurtle(createModel(body));
    return createModel(body);
  }

  public ExpandedPublication transform(Model publication) {
    // TODO: Set base NVA context from local JSON file
    // TODO: Set base NVI context from local JSON file
    // TODO: Split references between NVI and NVA contexts in SPARQL query
    var turtles = toTurtle(publication);
    try {
      var queryExecution = QueryExecutionFactory.create(PUBLICATION_SPARQL, publication);
      var model = queryExecution.execConstruct();
      var superTurtles = toTurtle(model);
      var document = JsonDocument.of(toJsonReader(model));
      var frame = IoUtils.inputStreamFromResources(Path.of("publicationDtoFrame.json"));
      var context = JsonDocument.of(frame);
      var jsonString = JsonLd.frame(document, context).get().toString();
      var parsedJson = ExpandedPublication.from(jsonString);
      return parsedJson;
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (JsonLdError e) {
      throw new RuntimeException(e);
    }
  }

  private JsonNode extractBody(String content) {
    try {
      var replacementContext = dtoObjectMapper.readTree(contextString);
      var document = dtoObjectMapper.readTree(content);
      var body = (ObjectNode) document.at(JSON_PTR_BODY);
      //      body.set(CONTEXT_NODE, replacementContext);
      return body;
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static StringReader toJsonReader(Model resultModel) {
    var outputStream = new ByteArrayOutputStream();
    RDFDataMgr.write(outputStream, resultModel, Lang.JSONLD);
    return new StringReader(outputStream.toString());
  }
}
