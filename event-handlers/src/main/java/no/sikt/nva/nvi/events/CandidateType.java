package no.sikt.nva.nvi.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;

@JsonSerialize
@JsonSubTypes({
    @JsonSubTypes.Type(value = NonNviCandidate.class, name = "NonNviCandidate"),
    @JsonSubTypes.Type(value = NviCandidate.class, name = "NviCandidate")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface CandidateType permits NonNviCandidate, NviCandidate {

    URI publicationId();

}
