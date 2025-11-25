package no.sikt.nva.nvi.index.model;

import java.net.URI;
import no.unit.nva.commons.json.JsonSerializable;

public record PersistedIndexDocumentMessage(URI documentUri) implements JsonSerializable {}
