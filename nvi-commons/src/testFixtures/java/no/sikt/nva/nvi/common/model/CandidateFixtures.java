package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;

public class CandidateFixtures {

  public static Candidate setupRandomApplicableCandidate(
      CandidateRepository candidateRepository, PeriodRepository periodRepository) {
    var request = randomUpsertRequestBuilder().build();
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }

  public static UpsertRequestBuilder randomApplicableCandidateRequestBuilder(
      Map<URI, Collection<NviCreatorDto>> creatorsPerOrganization) {
    return randomUpsertRequestBuilder().withCreatorsAndPoints(creatorsPerOrganization);
  }

  public static Candidate setupRandomApplicableCandidate(
      TestScenario scenario, Map<URI, Collection<NviCreatorDto>> creatorsPerOrganization) {
    var candidateRequest =
        randomUpsertRequestBuilder().withCreatorsAndPoints(creatorsPerOrganization).build();
    return scenario.upsertCandidate(candidateRequest);
  }

  public static Candidate setupRandomApplicableCandidate(
      TestScenario scenario, int publicationYear) {
    var publicationDate = new PublicationDate(String.valueOf(publicationYear), null, null);
    var candidateRequest =
        randomUpsertRequestBuilder().withPublicationDate(publicationDate).build();
    return scenario.upsertCandidate(candidateRequest);
  }

  public static Candidate setupRandomApplicableCandidate(TestScenario scenario) {
    var candidateRequest = randomUpsertRequestBuilder().build();
    return scenario.upsertCandidate(candidateRequest);
  }
}
