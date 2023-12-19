package no.sikt.nva.nvi.index;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;

public record PersistedIndexDocumentMessage(URI documentUri) {

    public String asJsonString() {
        return attempt(() -> dtoObjectMapper.writeValueAsString(this)).orElseThrow();
    }
}
