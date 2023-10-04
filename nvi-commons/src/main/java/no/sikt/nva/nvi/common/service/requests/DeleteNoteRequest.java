package no.sikt.nva.nvi.common.service.requests;

import java.util.UUID;

public record DeleteNoteRequest(UUID noteId, String username) {

}
