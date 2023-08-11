package handlers.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonSerialize
public record InstitutionApprovals(@JsonProperty(INSTITUTION_ID) String institutionId,
                                   @JsonProperty(VERIFIED_CREATORS) List<String> verifiedCreators) {

    private static final String INSTITUTION_ID = "institutionId";
    private static final String VERIFIED_CREATORS = "verifiedCreators";
}
