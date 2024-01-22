package no.sikt.nva.nvi.events.cristin;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.unit.nva.commons.json.JsonSerializable;

public record EventReferenceWithContent(@JsonProperty("contents") CristinNviReport toCristinNviReport) implements JsonSerializable {
}
