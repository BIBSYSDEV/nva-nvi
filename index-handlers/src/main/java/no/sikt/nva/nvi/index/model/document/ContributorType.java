package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonSerialize
@JsonSubTypes({
    @JsonSubTypes.Type(value = NviContributor.class, name = "NviContributor"),
    @JsonSubTypes.Type(value = Contributor.class, name = "Contributor")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface ContributorType permits NviContributor, Contributor {

    @SuppressWarnings("PMD.ShortMethodName")
    String id();

    String name();

    String orcid();

    String role();

    List<OrganizationType> affiliations();
}
