package no.sikt.nva.nvi.index.model.document;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.net.URI;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.model.PersistedResource;
import no.sikt.nva.nvi.index.utils.CandidateToIndexDocumentMapper;
import no.unit.nva.auth.uriretriever.UriRetriever;

@JsonSerialize
public record IndexDocumentWithConsumptionAttributes(
    @JsonProperty(BODY) NviCandidateIndexDocument indexDocument,
    @JsonProperty(CONSUMPTION_ATTRIBUTES) ConsumptionAttributes consumptionAttributes) {

  private static final String CONSUMPTION_ATTRIBUTES = "consumptionAttributes";
  private static final String BODY = "body";

  public static IndexDocumentWithConsumptionAttributes from(NviCandidateIndexDocument document) {
    return new IndexDocumentWithConsumptionAttributes(
        document, ConsumptionAttributes.from(document.identifier()));
  }

  public static IndexDocumentWithConsumptionAttributes from(
      Candidate candidate, PublicationDto publicationDto) {
    var indexDocument =
        new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();
    var consumptionAttributes = ConsumptionAttributes.from(indexDocument.identifier());
    return new IndexDocumentWithConsumptionAttributes(indexDocument, consumptionAttributes);
  }

  public static IndexDocumentWithConsumptionAttributes from(
      Candidate candidate, PersistedResource persistedResource, UriRetriever uriRetriever) {
    var indexDocument =
        generateIndexDocument(candidate, uriRetriever, persistedResource.getExpandedResource());
    var consumptionAttributes = ConsumptionAttributes.from(indexDocument.identifier());
    return new IndexDocumentWithConsumptionAttributes(indexDocument, consumptionAttributes);
  }

  public URI persist(StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter)
      throws IOException {
    return storageWriter.write(this);
  }

  public String toJsonString() throws JsonProcessingException {
    return dtoObjectMapper.writeValueAsString(this);
  }

  private static NviCandidateIndexDocument generateIndexDocument(
      Candidate candidate, UriRetriever uriRetriever, JsonNode expandedResource) {
    return attempt(() -> NviCandidateIndexDocument.from(expandedResource, candidate, uriRetriever))
        .orElseThrow();
  }
}
