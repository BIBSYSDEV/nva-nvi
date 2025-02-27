package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.UpsertRequestBuilder.fromRequest;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class CandidateFixtures {

  public static Candidate randomApplicableCandidate(
      CandidateRepository candidateRepository, PeriodRepository periodRepository) {
    var request = randomUpsertRequestBuilder().build();
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }

  public static Candidate randomApplicableCandidate(
      TestScenario scenario, Map<URI, Collection<NviCreatorDto>> creatorsPerOrganization) {
    var candidateRequest =
        randomUpsertRequestBuilder().withCreatorsAndPoints(creatorsPerOrganization).build();
    return scenario.upsertCandidate(candidateRequest);
  }

  public static UpsertRequestBuilder setupApprovedCandidate(
      TestScenario scenario,
      URI approvedByOrg,
      Map<URI, Collection<NviCreatorDto>> creatorsPerOrganization) {
    var candidateRequest =
        randomUpsertRequestBuilder().withCreatorsAndPoints(creatorsPerOrganization).build();
    setupOpenPeriod(scenario, candidateRequest.publicationDate().year());
    var candidate = scenario.upsertCandidate(candidateRequest);

    scenario.updateApprovalStatus(candidate, ApprovalStatus.APPROVED, approvedByOrg);
    return fromRequest(candidateRequest);
  }
}
