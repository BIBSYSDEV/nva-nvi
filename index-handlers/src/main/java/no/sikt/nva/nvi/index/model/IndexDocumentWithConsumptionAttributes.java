package no.sikt.nva.nvi.index.model;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.net.URI;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.unit.nva.auth.uriretriever.UriRetriever;

@JsonSerialize
public record IndexDocumentWithConsumptionAttributes(
    @JsonProperty(BODY) NviCandidateIndexDocument indexDocument,
    @JsonProperty(CONSUMPTION_ATTRIBUTES) ConsumptionAttributes consumptionAttributes) {

    public static final String CONSUMPTION_ATTRIBUTES = "consumptionAttributes";
    public static final String BODY = "body";
    public static final String JSON_PTR_BODY = "/body";

    public static IndexDocumentWithConsumptionAttributes from(NviCandidateIndexDocument document,
                                                              Operation action) {
        return new IndexDocumentWithConsumptionAttributes(
            document, new ConsumptionAttributes(document.identifier(), action));
    }

    public static IndexDocumentWithConsumptionAttributes from(String dynamoDbOperationType,
                                                              Candidate candidate,
                                                              UriRetriever uriRetriever,
                                                              StorageReader<URI> storageReader) {
        var indexDocument = generateIndexDocument(candidate, uriRetriever, storageReader);
        var consumptionAttributes = getConsumptionAttributes(dynamoDbOperationType, indexDocument);
        return new IndexDocumentWithConsumptionAttributes(indexDocument, consumptionAttributes);
    }

    public URI persist(StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter) throws IOException {
        return storageWriter.write(this);
    }

    public String toJsonString() throws JsonProcessingException {
        return dtoObjectMapper.writeValueAsString(this);
    }

    private static ConsumptionAttributes getConsumptionAttributes(String dynamoDbOperationType,
                                                                  NviCandidateIndexDocument indexDocument) {
        return new ConsumptionAttributes(indexDocument.identifier(), Operation.parse(dynamoDbOperationType));
    }

    private static NviCandidateIndexDocument generateIndexDocument(Candidate candidate,
                                                                   UriRetriever uriRetriever,
                                                                   StorageReader<URI> storageReader) {
        return attempt(() -> {
            var expandedResource = getExpandedResourceFromBucket(candidate, storageReader);
            return NviCandidateIndexDocument.from(expandedResource, candidate, uriRetriever);
        }).orElseThrow();
    }

    private static JsonNode getExpandedResourceFromBucket(Candidate candidate,
                                                          StorageReader<URI> storageReader)
        throws JsonProcessingException {
        var bucketUri = candidate.getPublicationDetails().publicationBucketUri();
        var result = storageReader.read(bucketUri);
        return dtoObjectMapper.readTree(result).at(JSON_PTR_BODY);
    }
}
