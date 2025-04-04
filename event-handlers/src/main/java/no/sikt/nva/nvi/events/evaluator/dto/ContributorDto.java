package no.sikt.nva.nvi.events.evaluator.dto;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.events.evaluator.dto.AffiliationDto.ExpandedAffiliationDto;

/** A (hopefully temporary) DTO for converting JSON data from publication-api. */
public record ContributorDto(
    @JsonProperty("name") String name,
    @JsonProperty("id") URI id,
    @JsonProperty("verificationStatus") String verificationStatus,
    @JsonProperty("roleType") String role,
    @JsonProperty("affiliations") List<AffiliationDto> affiliations) {

  public static ContributorDto fromJsonNode(JsonNode jsonNode) {
    try {
      var contributor =
          dtoObjectMapper.readValue(jsonNode.toString(), ExpandedContributorDto.class);
      return contributor.toDto();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public record ExpandedContributorDto(
      @JsonProperty("identity") ExpandedIdentityDto expandedIdentity,
      @JsonProperty("role") ExpandedRoleDto role,
      @JsonProperty("affiliations") List<ExpandedAffiliationDto> expandedAffiliations) {

    public ExpandedContributorDto(
        ExpandedIdentityDto expandedIdentity,
        ExpandedRoleDto role,
        List<ExpandedAffiliationDto> expandedAffiliations) {
      this.expandedIdentity = expandedIdentity;
      this.role = role;
      this.expandedAffiliations =
          nonNull(expandedAffiliations) ? expandedAffiliations : emptyList();
    }

    public ContributorDto toDto() {
      var name = expandedIdentity().defaultName();
      var id = expandedIdentity().id();
      var verificationStatus = expandedIdentity().verificationStatus();
      var roleType = role().type();
      var affiliations =
          expandedAffiliations().stream().map(ExpandedAffiliationDto::toDto).toList();

      return new ContributorDto(name, id, verificationStatus, roleType, affiliations);
    }
  }

  public record ExpandedIdentityDto(
      @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) @JsonProperty("name")
          List<String> name,
      @JsonProperty("id") URI id,
      @JsonProperty("verificationStatus") String verificationStatus) {

    // FIXME: This is a temporary fix to handle the fact that the name field can be an array.
    // We should handle this properly in the whole chain, but for now we just take the first
    // element and discard other names.
    public String defaultName() {
      if (isNull(name) || name.isEmpty()) {
        return null;
      }
      return name.getFirst();
    }
  }

  public record ExpandedRoleDto(@JsonProperty("type") String type) {}
}
