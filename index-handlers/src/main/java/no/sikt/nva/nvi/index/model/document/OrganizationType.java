package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Organization.class, name = "Organization"),
  @JsonSubTypes.Type(value = NviOrganization.class, name = "NviOrganization")
})
public sealed interface OrganizationType permits Organization, NviOrganization {

  @SuppressWarnings("PMD.ShortMethodName")
  URI id();

  List<URI> partOf();
}
