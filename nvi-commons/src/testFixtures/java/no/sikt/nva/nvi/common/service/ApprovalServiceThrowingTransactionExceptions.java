package no.sikt.nva.nvi.common.service;

import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.exceptions.TransactionException;
import no.sikt.nva.nvi.common.model.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class ApprovalServiceThrowingTransactionExceptions extends ApprovalService {
  public ApprovalServiceThrowingTransactionExceptions(CandidateRepository candidateRepository) {
    super(candidateRepository);
  }

  @Override
  public void updateApproval(
      Candidate candidate, UpdateApprovalRequest request, UserInstance user) {
    throw new TransactionException("Fake failure simulating a transaction error");
  }
}
