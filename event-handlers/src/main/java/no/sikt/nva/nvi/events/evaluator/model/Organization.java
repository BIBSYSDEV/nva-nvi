package no.sikt.nva.nvi.events.evaluator.model;

import static java.util.Objects.nonNull;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.SingletonCollector;

public record Organization(@JsonProperty(ID) URI id, @JsonProperty(PART_OF) List<Organization> partOf)
    implements JsonSerializable {

    public static final String ID = "id";
    public static final String PART_OF = "partOf";

    @JsonIgnore
    public Organization getTopLevelOrg() {
        if (nonNull(partOf()) && !partOf().isEmpty()) {

            var organization = partOf().stream().collect(SingletonCollector.collect());

            while (hasPartOf(organization)) {
                organization = organization.partOf().stream().collect(SingletonCollector.collect());
            }

            return organization;
        }

        return this;
    }

    private static boolean hasPartOf(Organization org) {
        return nonNull(org.partOf()) && !org.partOf().isEmpty();
    }
}
