package no.sikt.nva.nvi.common.service;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;

import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoteService {
  private static final Logger LOGGER = LoggerFactory.getLogger(NoteService.class);
  private final CandidateRepository candidateRepository;

  public NoteService(CandidateRepository candidateRepository) {
    this.candidateRepository = candidateRepository;
  }

  @JacocoGenerated
  public static NoteService defaultNoteService() {
    var dynamoClient = defaultDynamoClient();
    return new NoteService(new CandidateRepository(dynamoClient));
  }

  public void createNote(Candidate candidate, CreateNoteRequest request) {
    LOGGER.info("Creating note for candidateId={}", candidate.identifier());
    var updatedItems = candidate.createNote(request);
    var updatedApprovals =
        updatedItems.updatedApproval().map(Approval::toDao).map(List::of).orElse(emptyList());
    var notesToAdd = List.of(updatedItems.note().toDao());

    candidateRepository.updateCandidateItems(
        candidate.toDao(), updatedApprovals, emptyList(), notesToAdd);
  }

  public void deleteNote(Candidate candidate, DeleteNoteRequest request) {
    LOGGER.info("Deleting note for candidateId={}", candidate.identifier());
    var noteToDelete = candidate.getNoteForDeletion(request);
    candidateRepository.deleteNote(candidate.identifier(), noteToDelete.noteIdentifier());
  }
}
