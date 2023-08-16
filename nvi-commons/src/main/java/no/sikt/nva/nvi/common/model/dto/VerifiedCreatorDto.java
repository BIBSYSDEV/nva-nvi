package no.sikt.nva.nvi.common.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;

@JsonSerialize
public record VerifiedCreatorDto(@JsonProperty(ID_FIELD) URI id,
                                 @JsonProperty(NVI_INSTITUTIONS_FIELD) List<URI> nviInstitutions) {

    public static final String ID_FIELD = "id";
    public static final String NVI_INSTITUTIONS_FIELD = "nviInstitutions";
}
