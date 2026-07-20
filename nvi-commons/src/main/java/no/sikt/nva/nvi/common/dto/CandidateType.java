package no.sikt.nva.nvi.common.dto;

import java.net.URI;

public sealed interface CandidateType
    permits UpsertNonNviCandidateRequest, UpsertNviCandidateRequest {
  URI publicationId();
}
