package no.sikt.nva.nvi.index.aws;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import org.junit.jupiter.api.Test;

class MultiIndexWriterTest {

  @Test
  void shouldThrowWhenCreatedWithEmptyClientList() {
    assertThrows(IllegalArgumentException.class, () -> new MultiIndexWriter(List.of()));
  }

  @Test
  void shouldWriteToSingleClient() {
    var client = mock(CandidateSearchClient.class);
    var writer = new MultiIndexWriter(List.of(client));
    var document = mock(NviCandidateIndexDocument.class);
    when(document.identifier()).thenReturn(UUID.randomUUID());

    writer.addDocumentToIndex(document);

    verify(client).addDocumentToIndex(document);
  }

  @Test
  void shouldWriteToAllClients() {
    var primaryClient = mock(CandidateSearchClient.class);
    var secondaryClient = mock(CandidateSearchClient.class);
    var writer = new MultiIndexWriter(List.of(primaryClient, secondaryClient));
    var document = mock(NviCandidateIndexDocument.class);
    when(document.identifier()).thenReturn(UUID.randomUUID());

    writer.addDocumentToIndex(document);

    verify(primaryClient).addDocumentToIndex(document);
    verify(secondaryClient).addDocumentToIndex(document);
  }

  @Test
  void shouldPropagateExceptionFromPrimaryClient() {
    var primaryClient = mock(CandidateSearchClient.class);
    var secondaryClient = mock(CandidateSearchClient.class);
    var writer = new MultiIndexWriter(List.of(primaryClient, secondaryClient));
    var document = mock(NviCandidateIndexDocument.class);
    when(document.identifier()).thenReturn(UUID.randomUUID());
    when(primaryClient.addDocumentToIndex(document))
        .thenThrow(new RuntimeException("primary fail"));

    assertThrows(RuntimeException.class, () -> writer.addDocumentToIndex(document));
    verify(secondaryClient, never()).addDocumentToIndex(any());
  }

  @Test
  void shouldLogAndContinueWhenSecondaryClientFails() {
    var primaryClient = mock(CandidateSearchClient.class);
    var secondaryClient = mock(CandidateSearchClient.class);
    var writer = new MultiIndexWriter(List.of(primaryClient, secondaryClient));
    var document = mock(NviCandidateIndexDocument.class);
    when(document.identifier()).thenReturn(UUID.randomUUID());
    when(secondaryClient.addDocumentToIndex(document))
        .thenThrow(new RuntimeException("secondary fail"));

    assertDoesNotThrow(() -> writer.addDocumentToIndex(document));
    verify(primaryClient).addDocumentToIndex(document);
  }

  @Test
  void shouldRemoveFromAllClients() {
    var primaryClient = mock(CandidateSearchClient.class);
    var secondaryClient = mock(CandidateSearchClient.class);
    var writer = new MultiIndexWriter(List.of(primaryClient, secondaryClient));
    var identifier = UUID.randomUUID();

    writer.removeDocumentFromIndex(identifier);

    verify(primaryClient).removeDocumentFromIndex(identifier);
    verify(secondaryClient).removeDocumentFromIndex(identifier);
  }

  @Test
  void shouldPropagateExceptionFromPrimaryClientOnRemove() {
    var primaryClient = mock(CandidateSearchClient.class);
    var writer = new MultiIndexWriter(List.of(primaryClient));
    var identifier = UUID.randomUUID();
    when(primaryClient.removeDocumentFromIndex(identifier))
        .thenThrow(new RuntimeException("primary fail"));

    assertThrows(RuntimeException.class, () -> writer.removeDocumentFromIndex(identifier));
  }

  @Test
  void shouldLogAndContinueWhenSecondaryClientFailsOnRemove() {
    var primaryClient = mock(CandidateSearchClient.class);
    var secondaryClient = mock(CandidateSearchClient.class);
    var writer = new MultiIndexWriter(List.of(primaryClient, secondaryClient));
    var identifier = UUID.randomUUID();
    when(secondaryClient.removeDocumentFromIndex(identifier))
        .thenThrow(new RuntimeException("secondary fail"));

    assertDoesNotThrow(() -> writer.removeDocumentFromIndex(identifier));
    verify(primaryClient).removeDocumentFromIndex(identifier);
  }
}
