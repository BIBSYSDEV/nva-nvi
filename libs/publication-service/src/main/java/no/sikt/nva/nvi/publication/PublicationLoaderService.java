package no.sikt.nva.nvi.publication;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.exceptions.ParsingException;
import no.sikt.nva.nvi.rdf.Graph;
import no.sikt.nva.nvi.rdf.GraphProjectionPipeline;
import no.sikt.nva.nvi.rdf.GraphValidation;
import no.sikt.nva.nvi.rdf.JsonLdFrame;
import no.sikt.nva.nvi.rdf.RdfProcessingException;
import no.sikt.nva.nvi.rdf.SparqlConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This utility class extracts and transforms expanded publications stored as JSON documents in an
 * S3 bucket. It parses the document into a publication source graph, validates it, projects it
 * through a pipeline of SPARQL queries into the NVI graph, validates that, and frames the result
 * into a PublicationDto.
 */
public class PublicationLoaderService {

  private static final String CONTEXT_NODE = "@context";
  private static final String JSON_PTR_BODY = "/body";
  private static final String INPUT_CONTEXT_FILE = "nva_context.json";
  private static final JsonNode INPUT_CONTEXT = getInputContext();
  private static final JsonLdFrame PUBLICATION_FRAME =
      JsonLdFrame.fromResource("publication_frame.json");
  private static final GraphProjectionPipeline NVI_PROJECTIONS =
      new GraphProjectionPipeline(
          List.of(
              SparqlConstruct.fromResource("nva_normalization.rq"),
              SparqlConstruct.fromResource("nvi_projection.rq")));

  private final Logger logger = LoggerFactory.getLogger(PublicationLoaderService.class);
  private final StorageReader<URI> storageReader;

  public PublicationLoaderService(StorageReader<URI> storageReader) {
    this.storageReader = storageReader;
  }

  public PublicationDto extractAndTransform(URI publicationBucketUri) {
    logger.info("Extracting publication from S3 ({})", publicationBucketUri);
    var content = extractContentFromStorage(publicationBucketUri);
    var resultJson = projectToNviJson(content, publicationBucketUri);
    return toPublicationDto(resultJson, publicationBucketUri);
  }

  private PublicationDto toPublicationDto(String resultJson, URI publicationBucketUri) {
    try {
      logger.info("Transforming JSON-LD to PublicationDto ({})", publicationBucketUri);
      return PublicationDto.from(resultJson);
    } catch (JsonProcessingException exception) {
      logger.error(
          "Failed to transform JSON-LD to PublicationDto ({})", publicationBucketUri, exception);
      logger.error(resultJson);
      throw new ParsingException(exception.getMessage());
    }
  }

  private String projectToNviJson(JsonNode content, URI publicationBucketUri) {
    var publicationGraph =
        PublicationGraph.fromJsonLd(ExpandedDocumentTool.prepareJsonNodeForModel(content));
    logIfNonConformant(publicationGraph.validate(new NvaGraphValidator()));

    logger.info("Projecting NVI data with SPARQL queries ({})", publicationBucketUri);
    var nviGraph = Graph.of(publicationGraph.model()).project(NVI_PROJECTIONS);
    logIfNonConformant(nviGraph.validate(new NviGraphValidator()));

    try {
      return nviGraph.frame(PUBLICATION_FRAME);
    } catch (RdfProcessingException exception) {
      logger.error("Failed to frame graph model as JSON-LD", exception);
      throw new ParsingException(exception.getMessage());
    }
  }

  private void logIfNonConformant(GraphValidation validationReport) {
    // TODO: once the validation is in place, we will throw exceptions at this point
    if (validationReport.isNonConformant()) {
      validationReport.log(logger);
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

  private static JsonNode getInputContext() {
    var inputStream = stringFromResources(Path.of(INPUT_CONTEXT_FILE));
    try {
      return dtoObjectMapper.readTree(inputStream);
    } catch (JsonProcessingException e) {
      throw new ParsingException(e.getMessage());
    }
  }
}
