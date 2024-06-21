package no.sikt.nva.nvi.common.client.model;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import no.unit.nva.commons.json.JsonSerializable;

public record User(@JsonProperty("viewingScope") ViewingScope viewingScope) implements JsonSerializable {

    public static User fromJson(String jsonString) {
        return attempt(() -> dtoObjectMapper.readValue(jsonString, User.class)).orElseThrow();
    }

    public record ViewingScope(@JsonProperty("includedUnits") List<String> includedUnits) {

        public Set<URI> getIncludedUnitUris() {
            return includedUnits.stream().map(URI::create).collect(Collectors.toSet());
        }
    }
}
