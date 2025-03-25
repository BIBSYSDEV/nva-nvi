package no.sikt.nva.nvi.common.etl;

import static no.sikt.nva.nvi.common.utils.GraphUtils.createModel;
import static no.sikt.nva.nvi.common.utils.GraphUtils.toTurtle;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.ioutils.IoUtils.inputStreamFromResources;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;

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
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This utility class is intended for extracting and transforming expanded publications
 * stored as JSON documents in an S3 bucket. It is a wrapper around a SPARQL query that
 * extracts and flattens relevant fields, and a JSON-LD frame that structures the output.
 */
public class PublicationLoader {
  private static final String CONTEXT_NODE = "@context";
  private static final String JSON_PTR_BODY = "/body";
  private static final String NVA_CONTEXT_JSONLD = "nva_context.json";
  private static final String PUBLICATION_FRAME_JSONLD = "publication_frame.json";
  private static final String PUBLICATION_SPARQL =
      stringFromResources(Path.of("publication_query.sparql"));
  private static final String STATIC_CONTEXT = stringFromResources(Path.of(NVA_CONTEXT_JSONLD));
  private final Logger logger = LoggerFactory.getLogger(PublicationLoader.class);
  private final StorageReader<URI> storageReader;

  public PublicationLoader(StorageReader<URI> storageReader) {
    this.storageReader = storageReader;
  }

  public Publication extractAndTransform(URI publicationBucketUri) {
    logger.info("Extracting and transforming publication from S3: {}", publicationBucketUri);
    var content = extractContentFromStorage(publicationBucketUri);
    var model = loadModelFromJson(content);
    var publication = transformToPublication(model);
    logger.info("Successfully transformed publication with ID: {}", publication.id());
    return publication;
  }

  private JsonNode extractContentFromStorage(URI publicationBucketUri) {
    logger.info("Extracting publication from S3: {}", publicationBucketUri);
    try {
      var jsonString = storageReader.read(publicationBucketUri);
      var jsonDocument = dtoObjectMapper.readTree(jsonString);
      var body = (ObjectNode) jsonDocument.at(JSON_PTR_BODY);
      return withReplacementContext(body);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private Model loadModelFromJson(JsonNode body) {
    logger.info("Querying JSON-LD document to generate RDF model");
    var model = createModel(body);
    var queryExecution = QueryExecutionFactory.create(PUBLICATION_SPARQL, model);
    return queryExecution.execConstruct();
  }

  private Publication transformToPublication(Model model) {
    logger.info("Transforming RDF model to simplified Publication object");
    var foo = toTurtle(model);
    try {
      var document = JsonDocument.of(toJsonReader(model));
      var context = JsonDocument.of(inputStreamFromResources(PUBLICATION_FRAME_JSONLD));
      var jsonString = JsonLd.frame(document, context).get().toString();
      return Publication.from(jsonString);
    } catch (JsonProcessingException | JsonLdError e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Replaces the context node in the JSON-LD document with a static copy to avoid network calls.
   * This static context should be kept in sync with the source
   * (https://api.nva.unit.no/publication/context).
   *
   * @param body Content of the JSON-LD document to be transformed
   * @return JsonNode with the context node replaced
   */
  private JsonNode withReplacementContext(ObjectNode body) {
    try {
      var replacementContext = dtoObjectMapper.readTree(STATIC_CONTEXT);
      body.set(CONTEXT_NODE, replacementContext);
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
