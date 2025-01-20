package no.sikt.nva.nvi.common.service.requests;

import java.util.UUID;

@FunctionalInterface
public interface FetchCandidateRequest {
  UUID identifier();
}
