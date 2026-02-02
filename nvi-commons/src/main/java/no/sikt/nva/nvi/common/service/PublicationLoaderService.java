package no.sikt.nva.nvi.common.service;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.exceptions.ParsingException;
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
public class PublicationLoaderService {
  private static final String CONTEXT_NODE = "@context";
  private static final String JSON_PTR_BODY = "/body";
  private static final String INPUT_CONTEXT_FILE = "nva_context.json";
  private static final JsonNode INPUT_CONTEXT = getInputContext();
  private static final String OUTPUT_FRAMING_CONTEXT_FILE = "publication_frame.json";
  private static final JsonDocument OUTPUT_FRAMING_CONTEXT = getOutputFramingContext();
  private static final String SPARQL_QUERY =
      stringFromResources(Path.of("publication_query.sparql"));

  private final Logger logger = LoggerFactory.getLogger(PublicationLoaderService.class);
  private final StorageReader<URI> storageReader;

  public PublicationLoaderService(StorageReader<URI> storageReader) {
    this.storageReader = storageReader;
  }

  public PublicationDto extractAndTransform(URI publicationBucketUri) {
    logger.info("Parsing expanded publication from S3 ({})", publicationBucketUri);

    logger.info("Extracting publication from S3 ({})", publicationBucketUri);
    var content = extractContentFromStorage(publicationBucketUri);
    var inputModel = createModel(content);

    logger.info("Parsing publication with SPARQL query ({})", publicationBucketUri);
    var resultJson = parseInputModelToJsonLd(inputModel);

    try {
      logger.info("Transforming JSON-LD to PublicationDto ({})", publicationBucketUri);
      return PublicationDto.from(resultJson);
    } catch (JsonProcessingException e) {
      logger.error("Failed to transform JSON-LD to PublicationDto ({})", publicationBucketUri);
      logger.error("Unexpected error when framing output JSON: {}", e.getMessage());
      logger.error(resultJson);
      throw new ParsingException(e.getMessage());
    }
  }

  /**
   * Extracts the body node from a JSON-LD document stored in S3. Note that this replaces the
   * context node in the JSON-LD document with a static copy to avoid network calls. This static
   * context should be kept in sync with the <a
   * href="https://api.nva.unit.no/publication/context">source</a>.
   */
  private JsonNode extractContentFromStorage(URI publicationBucketUri) {
    try {
      var jsonString = storageReader.read(publicationBucketUri);
      var jsonDocument = dtoObjectMapper.readTree(jsonString);
      var body = (ObjectNode) jsonDocument.at(JSON_PTR_BODY);
      body.set(CONTEXT_NODE, INPUT_CONTEXT);
      return body;
    } catch (JsonProcessingException e) {
      throw new ParsingException(e.getMessage());
    }
  }

  private String parseInputModelToJsonLd(Model inputModel) {
    try (var queryExecution = QueryExecutionFactory.create(SPARQL_QUERY, inputModel)) {
      var resultModel = queryExecution.execConstruct();
      var document = JsonDocument.of(toJsonReader(resultModel));
      return JsonLd.frame(document, OUTPUT_FRAMING_CONTEXT).get().toString();
    } catch (JsonLdError e) {
      throw new ParsingException(e.getMessage());
    }
  }

  private static StringReader toJsonReader(Model resultModel) {
    var outputStream = new ByteArrayOutputStream();
    RDFDataMgr.write(outputStream, resultModel, Lang.JSONLD);
    return new StringReader(outputStream.toString(StandardCharsets.UTF_8));
  }

  private static JsonNode getInputContext() {
    var inputStream = stringFromResources(Path.of(INPUT_CONTEXT_FILE));
    try {
      return dtoObjectMapper.readTree(inputStream);
    } catch (JsonProcessingException e) {
      throw new ParsingException(e.getMessage());
    }
  }

  private static JsonDocument getOutputFramingContext() {
    try {
      return JsonDocument.of(inputStreamFromResources(OUTPUT_FRAMING_CONTEXT_FILE));
    } catch (JsonLdError e) {
      throw new ParsingException(e.getMessage());
    }
  }
}
