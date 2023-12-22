package no.sikt.nva.nvi.index.model;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import no.sikt.nva.nvi.common.StorageReader;

public final class PersistedResource {

    private static final String JSON_PTR_BODY = "/body";
    private final String body;

    private PersistedResource(String body) {
        this.body = body;
    }

    public static PersistedResource fromUri(URI uri, StorageReader<URI> storageReader) {
        return attempt(() -> storageReader.read(uri))
                   .map(PersistedResource::new)
                   .orElseThrow();
    }

    public JsonNode getExpandedResource() {
        return attempt(() -> dtoObjectMapper.readTree(body).at(JSON_PTR_BODY)).orElseThrow();
    }
}
