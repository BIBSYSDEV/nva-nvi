package no.sikt.nva.nvi.rest.create;

import no.unit.nva.commons.json.JsonSerializable;

public record NviNoteRequest(String text) implements JsonSerializable {

}
