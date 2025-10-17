package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;

import java.util.Collection;
import java.util.Map;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class CandidateFixtures {

  public static Candidate setupRandomApplicableCandidate(TestScenario scenario) {
    var candidateRequest = randomUpsertRequestBuilder().build();
    return scenario.upsertCandidate(candidateRequest);
  }

  public static UpsertRequestBuilder randomApplicableCandidateRequestBuilder(
      Map<Organization, Collection<NviCreatorDto>> creatorsPerOrganization) {
    return randomUpsertRequestBuilder().withCreatorsAndPoints(creatorsPerOrganization);
  }

  public static Candidate setupRandomApplicableCandidate(
      TestScenario scenario, Map<Organization, Collection<NviCreatorDto>> creatorsPerOrganization) {
    var candidateRequest =
        randomUpsertRequestBuilder().withCreatorsAndPoints(creatorsPerOrganization).build();
    return scenario.upsertCandidate(candidateRequest);
  }

  public static Candidate setupRandomApplicableCandidate(
      TestScenario scenario, int publicationYear) {
    var publicationDate = new PublicationDateDto(String.valueOf(publicationYear), null, null);
    var candidateRequest =
        randomUpsertRequestBuilder().withPublicationDate(publicationDate).build();
    return scenario.upsertCandidate(candidateRequest);
  }
}
