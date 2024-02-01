package no.sikt.nva.nvi.index.model;

import java.util.List;

public class NviContributor extends Contributor {

    public NviContributor(String id, String name, String orcid, String role, List<Affiliation> affiliations,
                          boolean isNviContributor) {
        super(id, name, orcid, role, affiliations, isNviContributor);
    }
}
