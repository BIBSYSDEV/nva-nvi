package no.sikt.nva.nvi.common.dto;

import java.net.URI;

public record UpsertNonNviCandidateRequest(URI publicationId) implements CandidateType {}
