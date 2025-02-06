package no.sikt.nva.nvi.common.service.dto.issue;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(name = "UnverifiedCreatorExists", value = UnverifiedCreatorIssue.class),
  @JsonSubTypes.Type(
      name = "UnverifiedCreatorFromOrganizationIssue",
      value = UnverifiedCreatorFromOrganizationIssue.class),
})
public interface CandidateIssue {
  String GLOBAL_SCOPE = "Global";
  String ORGANIZATION_SCOPE = "Organization";

  String title();

  String scope();

  String detail();
}
