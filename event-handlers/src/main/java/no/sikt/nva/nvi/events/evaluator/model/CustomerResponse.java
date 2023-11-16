package no.sikt.nva.nvi.events.evaluator.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record CustomerResponse(@JsonProperty(NVI_INSTITUTION) boolean nviInstitution) {

    public static final String NVI_INSTITUTION = "nviInstitution";

}
