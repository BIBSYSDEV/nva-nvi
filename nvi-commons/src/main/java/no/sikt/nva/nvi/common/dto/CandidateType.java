package no.sikt.nva.nvi.common.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;

@JsonSerialize
@JsonSubTypes({
  @JsonSubTypes.Type(value = UpsertNonNviCandidateRequest.class, name = "UpsertNonNviCandidateRequest"),
  @JsonSubTypes.Type(value = UpsertNviCandidateRequest.class, name = "UpsertNviCandidateRequest")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface CandidateType
    permits UpsertNonNviCandidateRequest, UpsertNviCandidateRequest {
  URI publicationId();
}
