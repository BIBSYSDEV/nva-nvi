package no.sikt.nva.nvi.index.model.document;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.net.URI;
import no.sikt.nva.nvi.common.StorageWriter;

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

  public URI persist(StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter)
      throws IOException {
    return storageWriter.write(this);
  }

  public String toJsonString() throws JsonProcessingException {
    return dtoObjectMapper.writeValueAsString(this);
  }
}
