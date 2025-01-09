package no.sikt.nva.nvi.events.evaluator.dto;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;

/**
 * A (hopefully temporary) DTO for converting JSON data from publication-api.
 */
public record ContributorDto(@JsonProperty("name") String name, @JsonProperty("id") URI id,
                             @JsonProperty("verificationStatus") String verificationStatus,
                             @JsonProperty("roleType") String role,
                             @JsonProperty("affiliations") List<Affiliation> affiliations) {

    public static ContributorDto fromJsonNode(JsonNode jsonNode) {
        try {
            var contributor = dtoObjectMapper.readValue(jsonNode.toString(), Contributor.class);
            return contributor.toDto();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public record Contributor(@JsonProperty("identity") Identity identity, @JsonProperty("role") Role role,
                              @JsonProperty("affiliations") List<Affiliation> affiliations) {

        public ContributorDto toDto() {
            var name = identity().name();
            var id = identity().id();
            var verificationStatus = identity().verificationStatus();
            var roleType = role().type();

            return new ContributorDto(name, id, verificationStatus, roleType, affiliations);
        }
    }

    public record Identity(@JsonProperty("name") String name, @JsonProperty("id") URI id,
                           @JsonProperty("verificationStatus") String verificationStatus) {

    }

    public record Role(@JsonProperty("type") String type) {

    }

    public record Affiliation(@JsonProperty("id") URI id, @JsonProperty("countryCode") String countryCode) {

    }
}

