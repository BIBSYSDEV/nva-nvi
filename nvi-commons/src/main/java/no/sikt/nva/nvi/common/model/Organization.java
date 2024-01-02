package no.sikt.nva.nvi.common.model;

import static java.util.Objects.nonNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.SingletonCollector;

public record Organization(@JsonProperty(ID) URI id,
                           @JsonProperty(PART_OF) List<Organization> partOf,
                           @JsonProperty(LABELS) Map<String, String> labels,
                           @JsonProperty(TYPE) String type,
                           @JsonProperty(CONTEXT) String context)
    implements JsonSerializable {

    private static final String ID = "id";
    private static final String PART_OF = "partOf";
    private static final String LABELS = "labels";
    private static final String TYPE = "type";
    private static final String CONTEXT = "@context";

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

    public String asJsonString() throws JsonProcessingException {
        return dtoObjectMapper.writeValueAsString(this);
    }

    private static boolean hasPartOf(Organization org) {
        return nonNull(org.partOf()) && !org.partOf().isEmpty();
    }
}
