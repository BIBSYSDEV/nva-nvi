package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("NviContributor")
public class NviContributor extends Contributor {
    public NviContributor(String id, String name, String orcid, String role, List<Affiliation> affiliations) {
        super(id, name, orcid, role, affiliations);
    }
}
