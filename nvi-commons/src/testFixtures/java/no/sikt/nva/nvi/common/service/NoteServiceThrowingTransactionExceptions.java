package no.sikt.nva.nvi.common.service;

import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.exceptions.TransactionException;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class NoteServiceThrowingTransactionExceptions extends NoteService {

  public NoteServiceThrowingTransactionExceptions(CandidateRepository candidateRepository) {
    super(candidateRepository);
  }

  @Override
  public void createNote(Candidate candidate, CreateNoteRequest request) {
    throw new TransactionException("Fake failure simulating a transaction error");
  }
}
