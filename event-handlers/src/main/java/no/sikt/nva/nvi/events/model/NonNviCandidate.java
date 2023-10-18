package no.sikt.nva.nvi.events.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import no.sikt.nva.nvi.common.service.requests.UpsertNonCandidateRequest;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record NonNviCandidate(URI publicationId) implements CandidateType, UpsertNonCandidateRequest {

}