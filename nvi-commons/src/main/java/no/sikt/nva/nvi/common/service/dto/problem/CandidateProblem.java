package no.sikt.nva.nvi.common.service.dto.problem;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(name = "UnverifiedCreatorExists", value = UnverifiedCreatorProblem.class),
  @JsonSubTypes.Type(
      name = "UnverifiedCreatorFromOrganizationProblem",
      value = UnverifiedCreatorFromOrganizationProblem.class),
})
public interface CandidateProblem {
  String GLOBAL_SCOPE = "Global";
  String ORGANIZATION_SCOPE = "Organization";

  String title();

  String scope();

  String detail();
}
