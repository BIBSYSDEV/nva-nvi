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

    public static ContributorDto fromJson(Contributor contributor) {
        var name = contributor
                       .identity()
                       .name();
        var id = contributor
                     .identity()
                     .id();
        var verificationStatus = contributor
                                     .identity()
                                     .verificationStatus();
        var roleType = contributor
                           .role()
                           .type();

        return new ContributorDto(name, id, verificationStatus, roleType, contributor.affiliations());
    }

    public static Contributor fromJsonNode(JsonNode jsonNode) {
        try {
            return dtoObjectMapper.readValue(jsonNode.toString(), Contributor.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public record Contributor(@JsonProperty("identity") Identity identity, @JsonProperty("role") Role role,
                              @JsonProperty("affiliations") List<Affiliation> affiliations) {

    }

    public record Identity(@JsonProperty("name") String name, @JsonProperty("id") URI id,
                           @JsonProperty("verificationStatus") String verificationStatus) {

    }

    public record Role(@JsonProperty("type") String type) {

    }

    public record Affiliation(@JsonProperty("id") URI id, @JsonProperty("countryCode") String countryCode) {

    }
}

