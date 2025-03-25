package no.sikt.nva.nvi.common.etl;

import static no.sikt.nva.nvi.common.utils.GraphUtils.createModel;
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
      stringFromResources(Path.of("sparql/publication.sparql"));
  private final Logger logger = LoggerFactory.getLogger(PublicationLoader.class);
  private final StorageReader<URI> storageReader;
  private static final String contextString = stringFromResources(Path.of(NVA_CONTEXT_JSONLD));

  public PublicationLoader(StorageReader<URI> storageReader) {
    this.storageReader = storageReader;
  }

  public Publication extractAndTransform(URI publicationBucketUri) {
    logger.info("Extracting and transforming publication from S3: {}", publicationBucketUri);
    var model = extract(publicationBucketUri);
    return transform(model);
  }

  public Model extract(URI publicationBucketUri) {
    var document = storageReader.read(publicationBucketUri);
    var body = extractBody(document);
    return createModel(body);
  }

  public Publication transform(Model publicationModel) {
    try {
      var queryExecution = QueryExecutionFactory.create(PUBLICATION_SPARQL, publicationModel);
      var model = queryExecution.execConstruct();
      var document = JsonDocument.of(toJsonReader(model));
      var context = JsonDocument.of(inputStreamFromResources(Path.of(PUBLICATION_FRAME_JSONLD)));
      var jsonString = JsonLd.frame(document, context).get().toString();
      var publication = Publication.from(jsonString);
      logger.info("Transformed publication with ID: {}", publication.id());
      return publication;
    } catch (JsonProcessingException | JsonLdError e) {
      throw new RuntimeException(e);
    }
  }

  private JsonNode extractBody(String content) {
    try {
      var document = dtoObjectMapper.readTree(content);
      var body = (ObjectNode) document.at(JSON_PTR_BODY);
      return replaceContextNode(body);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /*
   * Replaces the context node in the JSON-LD document with a static copy
   * to avoid network calls. This static context should be kept in sync with
   * the source at https://api.nva.unit.no/publication/context
   */
  private JsonNode replaceContextNode(ObjectNode body) {
    try {
      var replacementContext = dtoObjectMapper.readTree(contextString);
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
