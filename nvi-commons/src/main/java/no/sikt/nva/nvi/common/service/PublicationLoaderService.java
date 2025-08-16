package no.sikt.nva.nvi.common.service;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.file.Path;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.exceptions.ParsingException;
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

  private final Logger logger = LoggerFactory.getLogger(PublicationLoaderService.class);
  private final StorageReader<URI> storageReader;

  public PublicationLoaderService(StorageReader<URI> storageReader) {
    this.storageReader = storageReader;
  }

  public PublicationDto extractAndTransform(URI publicationBucketUri) {
    logger.info("Parsing expanded publication from S3 ({})", publicationBucketUri);

    logger.info("Extracting publication from S3 ({})", publicationBucketUri);
    var content = extractContentFromStorage(publicationBucketUri);
    var graph = extractNvaGraph(content);
    var resultJson = extractNviData(graph).toJsonLd();
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

  private NviGraph extractNviData(NvaGraph graph) {
    var nviGraph = graph.toNviGraph();
    var nviValidationReport = nviGraph.validate(new NviGraphValidator());
    // TODO: once the validation is in place, we will throw exceptions at this point
    if (nviValidationReport.isNonConformant()) {
      nviValidationReport.log(logger);
    }
    return nviGraph;
  }

  private NvaGraph extractNvaGraph(JsonNode content) {
    var graph = NvaGraph.fromJsonLd(content);
    var nvaValidator = new NvaGraphValidator();
    var nvaValidationReport = graph.validate(nvaValidator);
    // TODO: once the validation is in place, we will throw exceptions at this point
    if (nvaValidationReport.isNonConformant()) {
      nvaValidationReport.log(logger);
    }
    return graph;
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

  private static JsonNode getInputContext() {
    var inputStream = stringFromResources(Path.of(INPUT_CONTEXT_FILE));
    try {
      return dtoObjectMapper.readTree(inputStream);
    } catch (JsonProcessingException e) {
      throw new ParsingException(e.getMessage());
    }
  }
}
