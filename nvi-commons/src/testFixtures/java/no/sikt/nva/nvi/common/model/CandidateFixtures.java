package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.test.UpsertRequestBuilder.randomUpsertRequestBuilder;

import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class CandidateFixtures {

  public static Candidate randomApplicableCandidate(
      CandidateRepository candidateRepository, PeriodRepository periodRepository) {
    var request = randomUpsertRequestBuilder().build();
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }
}
