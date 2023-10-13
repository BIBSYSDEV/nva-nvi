package no.sikt.nva.nvi.events.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record NonNviCandidate(URI publicationId) implements CandidateType {

}