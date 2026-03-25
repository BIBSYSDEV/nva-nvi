package no.sikt.nva.nvi.index.aws;

import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import nva.commons.core.JacocoGenerated;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiIndexWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(MultiIndexWriter.class);
  private final List<CandidateSearchClient> clients;

  public MultiIndexWriter(List<CandidateSearchClient> clients) {
    if (clients.isEmpty()) {
      throw new IllegalArgumentException("At least one write index client is required");
    }
    this.clients = clients;
  }

  @JacocoGenerated
  public static MultiIndexWriter fromEnvironment() {
    var writeIndices = WriteIndexResolver.resolveWriteIndices();
    var openSearchClient = OpenSearchClientFactory.createAuthenticatedClient();
    var clients =
        writeIndices.stream()
            .map(indexName -> CandidateSearchClient.forIndex(openSearchClient, indexName))
            .toList();
    return new MultiIndexWriter(clients);
  }

  public IndexResponse addDocumentToIndex(NviCandidateIndexDocument indexDocument) {
    var primaryResponse = primaryClient().addDocumentToIndex(indexDocument);
    writeToSecondaryClients(
        client -> {
          client.addDocumentToIndex(indexDocument);
          return null;
        },
        "add document " + indexDocument.identifier());
    return primaryResponse;
  }

  public DeleteResponse removeDocumentFromIndex(UUID identifier) {
    var primaryResponse = primaryClient().removeDocumentFromIndex(identifier);
    writeToSecondaryClients(
        client -> {
          client.removeDocumentFromIndex(identifier);
          return null;
        },
        "remove document " + identifier);
    return primaryResponse;
  }

  private CandidateSearchClient primaryClient() {
    return clients.getFirst();
  }

  private void writeToSecondaryClients(
      java.util.function.Function<CandidateSearchClient, Void> operation,
      String operationDescription) {
    for (var i = 1; i < clients.size(); i++) {
      try {
        operation.apply(clients.get(i));
      } catch (Exception e) {
        LOGGER.warn(
            "Failed to {} on secondary write index (index {}): {}",
            operationDescription,
            i,
            e.getMessage());
      }
    }
  }
}
