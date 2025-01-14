package no.sikt.nva.nvi.events.evaluator.dto;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.events.evaluator.dto.AffiliationDto.ExpandedAffiliationDto;

/**
 * A (hopefully temporary) DTO for converting JSON data from publication-api.
 */
public record ContributorDto(@JsonProperty("name") String name, @JsonProperty("id") URI id,
                             @JsonProperty("verificationStatus") String verificationStatus,
                             @JsonProperty("roleType") String role,
                             @JsonProperty("affiliations") List<AffiliationDto> affiliations) {

    public static ContributorDto fromJsonNode(JsonNode jsonNode) {
        try {
            var contributor = dtoObjectMapper.readValue(jsonNode.toString(), ExpandedContributorDto.class);
            return contributor.toDto();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public record ExpandedContributorDto(@JsonProperty("identity") ExpandedIdentityDto expandedIdentity,
                                         @JsonProperty("role") ExpandedRoleDto role,
                                         @JsonProperty("affiliations") List<ExpandedAffiliationDto> expandedAffiliations) {

        public ContributorDto toDto() {
            var name = expandedIdentity().name();
            var id = expandedIdentity().id();
            var verificationStatus = expandedIdentity().verificationStatus();
            var roleType = role().type();
            var affiliations = expandedAffiliations()
                                   .stream()
                                   .map(ExpandedAffiliationDto::toDto)
                                   .toList();

            return new ContributorDto(name, id, verificationStatus, roleType, affiliations);
        }
    }

    public record ExpandedIdentityDto(@JsonProperty("name") String name, @JsonProperty("id") URI id,
                                      @JsonProperty("verificationStatus") String verificationStatus) {

    }

    public record ExpandedRoleDto(@JsonProperty("type") String type) {

    }
}

