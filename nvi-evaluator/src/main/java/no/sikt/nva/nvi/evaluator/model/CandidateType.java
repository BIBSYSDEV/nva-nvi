package no.sikt.nva.nvi.evaluator.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonSubTypes({
    @JsonSubTypes.Type(value = NonNviCandidate.class, name = "NonNviCandidate"),
    @JsonSubTypes.Type(value = NviCandidate.class, name = "NviCandidate")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface CandidateType permits NonNviCandidate, NviCandidate {

}
