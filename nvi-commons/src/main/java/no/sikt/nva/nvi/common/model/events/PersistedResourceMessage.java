package no.sikt.nva.nvi.common.model.events;

import java.net.URI;
import no.unit.nva.commons.json.JsonSerializable;

public record PersistedResourceMessage(URI resourceFileUri) implements JsonSerializable {}
