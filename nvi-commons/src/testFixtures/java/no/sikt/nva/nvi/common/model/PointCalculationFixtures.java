package no.sikt.nva.nvi.common.model;

import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.service.model.PointCalculation;

public class PointCalculationFixtures {

  public static PointCalculation pointCalculationFromRequest(UpsertNviCandidateRequest request) {
    return PointCalculation.from(request);
  }
}
