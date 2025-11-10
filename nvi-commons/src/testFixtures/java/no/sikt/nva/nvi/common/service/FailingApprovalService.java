package no.sikt.nva.nvi.common.service;

import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.exceptions.TransactionException;
import no.sikt.nva.nvi.common.model.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class FailingApprovalService extends ApprovalService {
    public FailingApprovalService(CandidateRepository candidateRepository) {
        super(candidateRepository);
    }

    @Override
    public void updateApproval(Candidate candidate, UpdateApprovalRequest request, UserInstance user) {
        throw new TransactionException("Fake failure due to concurrent update");
    }
}
